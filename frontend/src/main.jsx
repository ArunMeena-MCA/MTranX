import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.jsx";
import FlexcubeDashboard from "./pages/FlexcubeDashboard.jsx";
import "./index.css";

// No router library pulled in for one extra page - a plain path check is enough (this project has
// exactly two pages) and Vite's dev server already falls back unknown paths to index.html by
// default (appType "spa"), so navigating straight to /Dashboard works in dev without any extra
// config. A production static-file deployment (e.g. served by Spring or nginx) would need the
// same SPA-fallback behavior configured on that server for a direct link to /Dashboard to work -
// not yet set up, since no such deployment exists in this project today.
const isDashboardRoute = /^\/dashboard\/?$/i.test(window.location.pathname);

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>{isDashboardRoute ? <FlexcubeDashboard /> : <App />}</React.StrictMode>
);
