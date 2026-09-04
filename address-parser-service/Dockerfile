# Builds libpostal from source (it has no prebuilt package for most distros)
# before installing the Python binding. This is the standard install path
# documented at https://github.com/openvenues/libpostal#installation-maclinux -
# NOT something invented for this project.
#
# WARNING: this downloads ~2GB of trained model data (via `make install`,
# which runs libpostal's own data-download step) and compiles a C library -
# expect a slow first build (compilation + download time depends on network
# speed) and a large final image. Run this build with real disk space and
# time budgeted; it is not a quick `pip install`.
FROM python:3.11-slim AS libpostal-builder

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        autoconf \
        automake \
        libtool \
        pkg-config \
        build-essential \
        git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt
RUN git clone --depth 1 https://github.com/openvenues/libpostal
WORKDIR /opt/libpostal
# --datadir points at where the ~2GB of trained model data lands - keep it
# out of the container's ephemeral layers if you rebuild often; a named
# volume mounted at this path avoids re-downloading on every image rebuild.
RUN ./bootstrap.sh \
    && ./configure --datadir=/opt/libpostal_data \
    && make -j"$(nproc)" \
    && make install \
    && ldconfig

FROM python:3.11-slim
COPY --from=libpostal-builder /usr/local/lib/libpostal* /usr/local/lib/
COPY --from=libpostal-builder /usr/local/include/libpostal /usr/local/include/libpostal
COPY --from=libpostal-builder /opt/libpostal_data /opt/libpostal_data
RUN ldconfig

WORKDIR /app
COPY requirements.txt .
# postal (pypostal) links against the libpostal shared library copied above
# at install time - it must be present before this step, which is why the
# builder stage runs first.
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

EXPOSE 8090
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090"]
