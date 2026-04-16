const state = {
  token: "",
  routeResults: [],
};

const els = {
  userId: document.getElementById("userId"),
  manualToken: document.getElementById("manualToken"),
  issueTokenBtn: document.getElementById("issueTokenBtn"),
  useManualTokenBtn: document.getElementById("useManualTokenBtn"),
  authMessage: document.getElementById("authMessage"),
  tokenPreview: document.getElementById("tokenPreview"),
  serverHealth: document.getElementById("serverHealth"),
  lastResponse: document.getElementById("lastResponse"),
  lastHeaders: document.getElementById("lastHeaders"),
  burstRoute: document.getElementById("burstRoute"),
  burstCount: document.getElementById("burstCount"),
  burstConcurrency: document.getElementById("burstConcurrency"),
  burstBtn: document.getElementById("burstBtn"),
  burstLog: document.getElementById("burstLog"),
  mAllowed: document.getElementById("mAllowed"),
  mRejected: document.getElementById("mRejected"),
  mErrors: document.getElementById("mErrors"),
  mLatency: document.getElementById("mLatency"),
  barAllowed: document.getElementById("barAllowed"),
  barRejected: document.getElementById("barRejected"),
  originText: document.getElementById("originText"),
};

function setMessage(text, tone = "normal") {
  els.authMessage.textContent = text;
  if (tone === "good") {
    els.authMessage.style.color = "#79e7bc";
  } else if (tone === "bad") {
    els.authMessage.style.color = "#ffb2a8";
  } else {
    els.authMessage.style.color = "#9fb5c8";
  }
}

function setToken(token) {
  state.token = token;
  els.tokenPreview.textContent = token || "(none)";
  if (token) {
    const short = token.length > 28 ? `${token.slice(0, 28)}...` : token;
    setMessage(`Token loaded: ${short}`, "good");
  } else {
    setMessage("No token loaded.");
  }
}

async function checkHealth() {
  try {
    const res = await fetch("/actuator/health");
    const payload = await res.json();
    els.serverHealth.textContent = `Health: ${payload.status || res.status}`;
    els.serverHealth.style.background = "rgba(45, 189, 133, 0.16)";
  } catch (err) {
    els.serverHealth.textContent = "Health: Unavailable";
    els.serverHealth.style.background = "rgba(255, 107, 90, 0.16)";
  }
}

async function issueDemoToken() {
  const userId = encodeURIComponent(els.userId.value.trim() || "alice");
  try {
    const res = await fetch(`/auth/token?userId=${userId}`, { method: "POST" });
    const payload = await res.json().catch(() => ({}));
    if (!res.ok || !payload.token) {
      setMessage("Token issue failed (likely disabled in prod profile). Paste a manual token.", "bad");
      return;
    }
    setToken(payload.token);
  } catch (err) {
    setMessage(`Token issue request failed: ${err.message}`, "bad");
  }
}

function useManualToken() {
  const token = els.manualToken.value.trim();
  if (!token) {
    setMessage("Paste a token first.", "bad");
    return;
  }
  setToken(token);
}

function readRateHeaders(res) {
  return {
    algorithm: res.headers.get("X-RateLimit-Algorithm") || "n/a",
    scope: res.headers.get("X-RateLimit-Scope") || "n/a",
    limit: res.headers.get("X-RateLimit-Limit") || "n/a",
    remaining: res.headers.get("X-RateLimit-Remaining") || "n/a",
    retryAfter: res.headers.get("Retry-After") || "n/a",
  };
}

async function probeRoute(route) {
  if (!state.token) {
    setMessage("Token required before probing /api routes.", "bad");
    return;
  }

  const started = performance.now();
  try {
    const res = await fetch(route, {
      headers: {
        Authorization: `Bearer ${state.token}`,
      },
    });

    const latency = Math.round(performance.now() - started);
    const textBody = await res.text();
    const headerView = readRateHeaders(res);

    let parsedBody = textBody;
    try {
      parsedBody = JSON.stringify(JSON.parse(textBody), null, 2);
    } catch (err) {
      // Keep plain text if not JSON.
    }

    els.lastResponse.textContent = [
      `Route: ${route}`,
      `Status: ${res.status}`,
      `Latency: ${latency} ms`,
      "Body:",
      parsedBody,
    ].join("\n");

    els.lastHeaders.textContent = JSON.stringify(headerView, null, 2);
  } catch (err) {
    els.lastResponse.textContent = `Probe failed for ${route}: ${err.message}`;
  }
}

function updateBurstMetrics({ allowed, rejected, errors, avgLatency }) {
  const total = Math.max(1, allowed + rejected + errors);
  const allowedPct = Math.round((allowed / total) * 100);
  const rejectedPct = Math.round((rejected / total) * 100);

  els.mAllowed.textContent = String(allowed);
  els.mRejected.textContent = String(rejected);
  els.mErrors.textContent = String(errors);
  els.mLatency.textContent = `${avgLatency} ms`;
  els.barAllowed.style.width = `${allowedPct}%`;
  els.barRejected.style.width = `${rejectedPct}%`;
}

async function runBurstTest() {
  if (!state.token) {
    setMessage("Token required before burst test.", "bad");
    return;
  }

  const route = els.burstRoute.value;
  const count = Number.parseInt(els.burstCount.value, 10);
  const concurrency = Number.parseInt(els.burstConcurrency.value, 10);

  if (!Number.isFinite(count) || !Number.isFinite(concurrency) || count <= 0 || concurrency <= 0) {
    els.burstLog.textContent = "Invalid burst settings.";
    return;
  }

  els.burstBtn.disabled = true;
  els.burstLog.textContent = `Running ${count} requests against ${route} with concurrency ${concurrency}...`;

  let allowed = 0;
  let rejected = 0;
  let errors = 0;
  let totalLatency = 0;
  const lines = [];

  const workers = Array.from({ length: concurrency }, (_, workerIndex) => (async () => {
    for (let i = workerIndex; i < count; i += concurrency) {
      const started = performance.now();
      try {
        const res = await fetch(route, {
          headers: {
            Authorization: `Bearer ${state.token}`,
          },
        });
        const latency = Math.round(performance.now() - started);
        totalLatency += latency;

        if (res.status === 429) {
          rejected += 1;
        } else if (res.ok) {
          allowed += 1;
        } else {
          errors += 1;
        }

        if (i < 8) {
          const h = readRateHeaders(res);
          lines.push(`#${i + 1} status=${res.status} alg=${h.algorithm} remaining=${h.remaining} retry=${h.retryAfter}`);
        }
      } catch (err) {
        errors += 1;
      }
    }
  })());

  await Promise.all(workers);

  const avgLatency = Math.round(totalLatency / Math.max(1, count));
  updateBurstMetrics({ allowed, rejected, errors, avgLatency });

  els.burstLog.textContent = [
    `Route: ${route}`,
    `Requests: ${count}`,
    `Allowed: ${allowed}`,
    `Rejected(429): ${rejected}`,
    `Other Errors: ${errors}`,
    `Avg Latency: ${avgLatency} ms`,
    "--- Sample responses ---",
    ...lines,
  ].join("\n");

  els.burstBtn.disabled = false;
}

function boot() {
  els.originText.textContent = window.location.origin;

  els.issueTokenBtn.addEventListener("click", issueDemoToken);
  els.useManualTokenBtn.addEventListener("click", useManualToken);
  els.burstBtn.addEventListener("click", runBurstTest);

  document.querySelectorAll("button[data-route]").forEach((btn) => {
    btn.addEventListener("click", () => probeRoute(btn.dataset.route));
  });

  checkHealth();
  setToken("");
}

boot();
