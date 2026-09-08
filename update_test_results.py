#!/usr/bin/env python3
"""Run Markdown MT test cases through the converter and update XML result blocks."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from pathlib import Path

CASE_PATTERN = re.compile(
    r"(?ms)(?P<prefix>^##\s+(?P<case_id>TC\d+)\b.*?^\s*\*\*Your XML result:\*\*\s*\r?\n"
    r"```[^\r\n]*\r?\n)(?P<result>.*?)(?P<suffix>^```)")
MT_BLOCK_PATTERN = re.compile(r"(?ms)^```[^\r\n]*\r?\n(?P<message>.*?)^```")
CONNECTION_ERROR_PATTERN = re.compile(
    r"(?i)(connection refused|connection reset|connect|timed out|timeout|could not resolve|network|curl error)"
)
UPSTREAM_503_PATTERN = re.compile(r"(?i)(?:http\s*503|status[\"']?\s*:\s*[\"']?503|code[\"']?\s*:\s*[\"']?503)")
UPSTREAM_429_PATTERN = re.compile(r"(?i)(?:http\s*429|status[\"']?\s*:\s*[\"']?429|code[\"']?\s*:\s*[\"']?429|quota exceeded|resource_exhausted|rate limit)")
RETRY_AFTER_SECONDS_PATTERN = re.compile(r"(?i)(?:retry(?:ing)?\s+in|retryDelay[\"']?\s*:\s*[\"']?)(\d+(?:\.\d+)?)\s*s")
HTTP_STATUS_MARKER = "__MTMX_HTTP_STATUS__:"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Update MT test-case result blocks using the local conversion API."
    )
    parser.add_argument(
        "files",
        nargs="*",
        type=Path,
        default=[Path(f"TC batch-{number}.md") for number in range(7, 8)],
        help="Markdown batch files (default: TC batch-2.md through TC batch-5.md).",
    )
    parser.add_argument("--url", default="http://localhost:8000/api/convert")
    parser.add_argument("--source-format", default="MT103")
    parser.add_argument("--target-format", default="PACS00800108")
    parser.add_argument("--dry-run", action="store_true", help="Parse and call nothing; do not modify files.")
    parser.add_argument("--retries", type=int, default=3, help="Maximum attempts for connection failures.")
    parser.add_argument("--retry-delay", type=float, default=5.0, help="Seconds between retries.")
    parser.add_argument("--request-delay", type=float, default=5.0, help="Seconds before each new case request.")
    return parser.parse_args()


def extract_message(case_text: str, case_id: str) -> str:
    match = MT_BLOCK_PATTERN.search(case_text)
    if not match:
        raise ValueError(f"{case_id}: no fenced MT message found")
    message = match.group("message").strip()
    if not message.startswith("{1:") or "{4:" not in message:
        raise ValueError(f"{case_id}: first fenced block does not look like an MT message")
    return message


def is_retryable_failure(text: str, http_status: int | None = None) -> bool:
    return (
        http_status in (429, 503)
        or bool(CONNECTION_ERROR_PATTERN.search(text))
        or bool(UPSTREAM_503_PATTERN.search(text))
        or bool(UPSTREAM_429_PATTERN.search(text))
    )


def retry_wait_seconds(text: str, default: float) -> float:
    match = RETRY_AFTER_SECONDS_PATTERN.search(text)
    return max(default, float(match.group(1))) if match else default


def call_converter(
    url: str,
    message: str,
    source_format: str,
    target_format: str,
    case_id: str,
    retries: int,
    retry_delay: float,
) -> str:
    payload = json.dumps(
        {
            "raw_text": message,
            "source_format": source_format,
            "target_format": target_format,
        },
        ensure_ascii=False,
    ).encode("utf-8")
    command = [
        "curl.exe",
        "--silent",
        "--show-error",
        "--fail-with-body",
        "--request",
        "POST",
        url,
        "--header",
        "Content-Type: application/json",
        "--data-binary",
        "@-",
        "--write-out",
        f"\n{HTTP_STATUS_MARKER}%{{http_code}}",
    ]
    max_attempts = max(1, retries)
    for attempt in range(1, max_attempts + 1):
        print(f"{case_id}: request attempt {attempt}/{max_attempts}")
        completed = subprocess.run(command, input=payload, capture_output=True)
        output = completed.stdout.decode("utf-8", errors="replace").strip()
        stderr = completed.stderr.decode("utf-8", errors="replace").strip()
        status_match = re.search(rf"{re.escape(HTTP_STATUS_MARKER)}(\d{{3}})$", output)
        http_status = int(status_match.group(1)) if status_match else None
        if status_match:
            output = output[: status_match.start()].rstrip()

        if completed.returncode != 0:
            error = output or stderr or f"curl failed with exit code {completed.returncode}"
            if is_retryable_failure(error, http_status) and attempt < max_attempts:
                wait = retry_wait_seconds(error, retry_delay)
                print(f"{case_id}: retryable connection/quota error; retrying in {wait:g}s", file=sys.stderr)
                time.sleep(wait)
                continue
            return error

        try:
            response = json.loads(output)
        except json.JSONDecodeError:
            return output

        rendered = response.get("rendered_output")
        if rendered:
            return rendered.rstrip("\r\n")

        detail = response.get("detail", response)
        if isinstance(detail, dict):
            result = json.dumps(detail, ensure_ascii=False, indent=2)
        else:
            result = str(detail)

        if is_retryable_failure(result) and attempt < max_attempts:
            wait = retry_wait_seconds(result, retry_delay)
            print(f"{case_id}: upstream connection/quota error; retrying in {wait:g}s", file=sys.stderr)
            time.sleep(wait)
            continue
        return result

    return "converter failed without a response"


def update_file(
    path: Path,
    url: str,
    source_format: str,
    target_format: str,
    dry_run: bool,
    retries: int,
    retry_delay: float,
    request_delay: float,
) -> tuple[int, int]:
    original = path.read_text(encoding="utf-8")
    matches = list(CASE_PATTERN.finditer(original))
    if not matches:
        raise ValueError("no test-case result blocks found")

    replacements: list[tuple[int, int, str]] = []
    for match in matches:
        case_id = match.group("case_id")
        message = extract_message(match.group(0), case_id)
        print(f"\n===== {case_id} MT INPUT =====")
        print(message)
        if dry_run:
            result = "<dry-run: converter call skipped>"
        else:
            print(f"{case_id}: waiting {request_delay:g}s before request")
            time.sleep(max(0.0, request_delay))
            result = call_converter(
                url,
                message,
                source_format,
                target_format,
                case_id,
                retries,
                retry_delay,
            )
        print(f"===== {case_id} CONVERTER OUTPUT =====")
        print(result)
        replacements.append((match.start("result"), match.end("result"), result + "\n"))
        print(f"{path}: {case_id} updated")

    if not dry_run:
        updated = original
        for start, end, result in reversed(replacements):
            updated = updated[:start] + result + updated[end:]
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(updated, encoding="utf-8", newline="")
        temporary.replace(path)

    return len(matches), 0


def main() -> int:
    args = parse_args()
    total = 0
    try:
        for path in args.files:
            if not path.is_file():
                raise FileNotFoundError(path)
            count, _ = update_file(
                path,
                args.url,
                args.source_format,
                args.target_format,
                args.dry_run,
                args.retries,
                args.retry_delay,
                args.request_delay,
            )
            total += count
    except (OSError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    action = "validated" if args.dry_run else "updated"
    print(f"{action} {total} test cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
