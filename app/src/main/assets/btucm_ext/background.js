"use strict";

const BTU_ORIGIN_URL = "https://classroom.btu.edu.ge/";
const BTU_HOST = "classroom.btu.edu.ge";
const SESSION_COOKIE_NAME = "argus_session";
const NATIVE_APP = "btucm_native";

let callbackObserved = false;
let authenticatedPageReached = false;
let lastAnonSent = false;
let lastReadyDelivered = false;
let lastKnownExpirationDate = null;
let expirationReported = false;
let nativePort = null;
let observationActive = true;

function isBtuDomain(domain) {
  if (!domain) return false;
  const clean = domain.toLowerCase().replace(/^\./, "");
  return clean === BTU_HOST;
}

function isBtuCookie(cookie) {
  if (!cookie || cookie.name !== SESSION_COOKIE_NAME) {
    return false;
  }
  return isBtuDomain(cookie.domain);
}

function isCookieExpired(cookie) {
  if (!cookie || typeof cookie.expirationDate !== "number") {
    return false;
  }
  return cookie.expirationDate <= Math.floor(Date.now() / 1000);
}

async function sendToNative(payload) {
  try {
    await browser.runtime.sendNativeMessage(NATIVE_APP, payload);
  } catch (_) {
    if (nativePort) {
      try {
        nativePort.postMessage(payload);
      } catch (_) {
        // Ignore when native listener is tearing down
      }
    }
  }
}

async function readBtuSessionCookie() {
  try {
    const direct = await browser.cookies.get({
      url: BTU_ORIGIN_URL,
      name: SESSION_COOKIE_NAME,
    });
    if (direct && direct.value && isBtuCookie(direct)) {
      return direct;
    }

    const stores = await browser.cookies.getAllCookieStores();
    for (const store of stores) {
      const candidate = await browser.cookies.get({
        url: BTU_ORIGIN_URL,
        name: SESSION_COOKIE_NAME,
        storeId: store.id,
      });
      if (candidate && candidate.value && isBtuCookie(candidate)) {
        return candidate;
      }
    }
  } catch (_) {
    // Ignore store errors
  }
  return null;
}

async function checkBtuSessionExpiration() {
  const cookie = await readBtuSessionCookie();
  if (!cookie) {
    if ((lastAnonSent || lastReadyDelivered) && !expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "cookie_missing_or_expired",
      });
    }
    return;
  }

  if (isCookieExpired(cookie)) {
    if (!expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "expiration_date_passed",
        expirationDate: cookie.expirationDate,
      });
    }
  } else {
    lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;
    expirationReported = false;
  }
}

async function reportInitialSessionIfPresent() {
  if (authenticatedPageReached) {
    return;
  }
  const cookie = await readBtuSessionCookie();
  if (cookie && cookie.value) {
    if (isCookieExpired(cookie)) {
      await checkBtuSessionExpiration();
      return;
    }
    lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;
    expirationReported = false;
    if (!lastAnonSent) {
      lastAnonSent = true;
      await sendToNative({
        type: "SESSION_DETECTED",
        authenticated: false,
        session: cookie.value,
        expirationDate: lastKnownExpirationDate,
      });
    }
  }
}

async function reportAuthenticatedSession() {
  const cookie = await readBtuSessionCookie();
  if (cookie && cookie.value) {
    if (isCookieExpired(cookie)) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "expiration_date_passed",
        expirationDate: cookie.expirationDate,
      });
      return;
    }
    lastReadyDelivered = true;
    expirationReported = false;
    lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;
    await sendToNative({
      type: "SESSION_READY",
      session: cookie.value,
      expirationDate: lastKnownExpirationDate,
    });
  } else {
    await sendToNative({
      type: "SESSION_COOKIE_MISSING",
    });
  }
}

function connectControlPort() {
  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
    nativePort.onMessage.addListener(async (msg) => {
      if (!msg || typeof msg !== "object") {
        return;
      }
      switch (msg.action) {
        case "PAUSE_SESSION_OBSERVATION":
          observationActive = false;
          break;
        case "RESET_STATE":
          observationActive = true;
          callbackObserved = false;
          authenticatedPageReached = false;
          lastAnonSent = false;
          lastReadyDelivered = false;
          lastKnownExpirationDate = null;
          expirationReported = false;
          break;
        case "QUERY_BTU_SESSION":
          observationActive = true;
          if (msg.expectAuthenticated) {
            authenticatedPageReached = true;
            await reportAuthenticatedSession();
          } else {
            await reportInitialSessionIfPresent();
          }
          break;
        case "CHECK_SESSION_EXPIRATION":
          await checkBtuSessionExpiration();
          break;
        default:
          break;
      }
    });
    nativePort.onDisconnect.addListener(() => {
      nativePort = null;
    });
  } catch (_) {
    nativePort = null;
  }
}

browser.cookies.onChanged.addListener(async (changeInfo) => {
  if (!observationActive) return;
  if (!isBtuCookie(changeInfo.cookie)) {
    return;
  }
  const cookie = changeInfo.cookie;

  if (changeInfo.removed) {
    const cause = changeInfo.cause || "removed";
    if ((lastAnonSent || lastReadyDelivered) && !expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: cause,
      });
    }
    return;
  }

  if (!cookie.value) {
    return;
  }

  if (isCookieExpired(cookie)) {
    if (!expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "expiration_date_passed",
        expirationDate: cookie.expirationDate,
      });
    }
    return;
  }

  expirationReported = false;
  lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;

  if (authenticatedPageReached) {
    lastReadyDelivered = true;
    await sendToNative({
      type: "SESSION_READY",
      session: cookie.value,
      expirationDate: lastKnownExpirationDate,
    });
  } else if (!lastAnonSent) {
    lastAnonSent = true;
    await sendToNative({
      type: "SESSION_DETECTED",
      authenticated: false,
      session: cookie.value,
      expirationDate: lastKnownExpirationDate,
    });
  }
});

async function handleBtuNavigation(details) {
  if (!observationActive) return;
  if (details.frameId !== 0 || !details.url) {
    return;
  }
  let parsedUrl;
  try {
    parsedUrl = new URL(details.url);
  } catch (_) {
    return;
  }

  if (parsedUrl.protocol !== "https:" || parsedUrl.hostname.toLowerCase() !== BTU_HOST ||
      (parsedUrl.port && parsedUrl.port !== "443")) {
    return;
  }

  const pathname = (parsedUrl.pathname || "/").replace(/^\/(?:ge|en)\//i, "/ge/");

  if (pathname.startsWith("/ge/login/oauth")) {
    if (!callbackObserved) {
      callbackObserved = true;
      await sendToNative({
        type: "BTU_CALLBACK_OBSERVED",
        hasError: parsedUrl.searchParams.has("error"),
      });
    }
    await reportInitialSessionIfPresent();
    return;
  }

  if (pathname.startsWith("/ge/student/")) {
    authenticatedPageReached = true;
    if (!lastReadyDelivered) {
      await sendToNative({ type: "AUTHENTICATED_PAGE_REACHED" });
      await reportAuthenticatedSession();
    }
    return;
  }

  if (pathname === "/" || pathname.startsWith("/ge/login/index")) {
    await reportInitialSessionIfPresent();
  }
}

const btuFilter = { url: [{ hostEquals: BTU_HOST }] };
browser.webNavigation.onCommitted.addListener(handleBtuNavigation, btuFilter);
browser.webNavigation.onCompleted.addListener(handleBtuNavigation, btuFilter);

setInterval(() => {
  if (observationActive && (lastKnownExpirationDate !== null || lastReadyDelivered || lastAnonSent)) {
    checkBtuSessionExpiration();
  }
}, 15000);

connectControlPort();
