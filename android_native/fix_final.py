p = "build/assets/index.html"
html = open(p, "r", encoding="utf-8").read().replace("v1.0.0", "v2.1.0")

html = html.replace(
    "import { getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult, onAuthStateChanged, signOut,",
    "import { getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult, signInWithCredential, onAuthStateChanged, signOut,"
)
html = html.replace(
    "getRedirectResult(auth)",
    "(window._fb = { auth, GoogleAuthProvider, signInWithPopup, signInWithCredential }, getRedirectResult(auth))"
)

bridge = """<style>
* { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; }
input, textarea, [contenteditable="true"] { -webkit-user-select: text !important; user-select: text !important; }
div[role="dialog"] button, .modal button, [class*="modal"] button { display: inline-flex !important; visibility: visible !important; opacity: 1 !important; }
</style>
<script>
(function() {
  const origURL = URL.createObjectURL, blobs = {};
  URL.createObjectURL = function(o) { const u = origURL.call(this, o); if (o instanceof Blob) blobs[u] = o; return u; };
  const origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function() {
    if (this.href && this.href.startsWith("blob:") && window.AndroidBridge && blobs[this.href]) {
      const f = this.download || ("AmarHisab_Backup_" + Date.now() + ".json");
      const r = new FileReader();
      r.onloadend = () => window.AndroidBridge.saveBase64File(r.result, f);
      r.readAsDataURL(blobs[this.href]);
      return;
    }
    return origClick.apply(this, arguments);
  };
})();
function applyAvatar() {
  const s = localStorage.getItem("amarHisab_customAvatar");
  if (!s) return;
  document.querySelectorAll(".avatar-wrap, #topAvatar, .profile-avatar").forEach(el => {
    if (el.tagName === "IMG") el.src = s;
    else el.innerHTML = `<img src="${s}" style="width:100%;height:100%;object-fit:cover;border-radius:9999px;">`;
  });
}
window.onNativeImagePicked = function(dataUrl) {
  localStorage.setItem("amarHisab_customAvatar", dataUrl);
  try {
    const bin = atob(dataUrl.split(",")[1]), bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    const dt = new DataTransfer();
    dt.items.add(new File([bytes], "avatar.jpg", { type: "image/jpeg" }));
    const inp = document.getElementById("picInput");
    if (inp) { inp.files = dt.files; inp.dispatchEvent(new Event("change", { bubbles: true })); }
  } catch (e) {}
  applyAvatar();
};
window.onNativeJsonPicked = function(b64) {
  try {
    const bin = atob(b64), bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    const dt = new DataTransfer();
    dt.items.add(new File([bytes], "backup.json", { type: "application/json" }));
    const inp = document.getElementById("impFile");
    if (inp) { inp.files = dt.files; inp.dispatchEvent(new Event("change", { bubbles: true })); }
  } catch (e) { alert("Import Error: " + e.message); }
};
if (location.search.includes("native_auth=1")) {
  window.addEventListener("load", () => {
    document.body.innerHTML = "<div style='display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;gap:16px;'><h2>Amar Hisab Login</h2><button id='goBtn' style='padding:14px 28px;font-size:16px;background:#16a34a;color:#fff;border:none;border-radius:10px;font-weight:bold;'>Continue with Google</button></div>";
    document.getElementById("goBtn").onclick = async () => {
      try {
        const fb = window._fb;
        const res = await fb.signInWithPopup(fb.auth, new fb.GoogleAuthProvider());
        const cred = fb.GoogleAuthProvider.credentialFromResult(res);
        const tok = cred ? cred.idToken : "";
        if (tok) location.href = "amarhisab://auth?token=" + encodeURIComponent(tok);
      } catch (e) { alert("Login Error: " + e.message); }
    };
  });
}
window.onNativeGoogleToken = function(idToken) {
  if (!idToken) return;
  localStorage.setItem("pending_gtoken", idToken);
  processPendingToken();
};
function processPendingToken() {
  const tok = localStorage.getItem("pending_gtoken");
  if (!tok) return;
  const t = setInterval(async () => {
    const fb = window._fb;
    if (!fb) return;
    clearInterval(t);
    localStorage.removeItem("pending_gtoken");
    try {
      const cred = fb.GoogleAuthProvider.credential(tok);
      await fb.signInWithCredential(fb.auth, cred);
      location.reload();
    } catch (e) { alert("Sign-in Error: " + e.message); }
  }, 200);
}
window.addEventListener("DOMContentLoaded", processPendingToken);
document.addEventListener("click", function(e) {
  if (!window.AndroidBridge) return;
  const t = e.target, btn = t.closest("button");
  if (btn && ((btn.textContent || "").includes("Sign in with Google") || (btn.textContent || "").includes("Google দিয়ে লগইন করুন"))) {
    e.preventDefault(); e.stopImmediatePropagation(); window.AndroidBridge.startGoogleLogin(); return;
  }
  if (t.closest(".avatar-wrap") || t.closest("[title='Change Photo']") || (btn && (btn.textContent || "").includes("Change Photo"))) {
    e.preventDefault(); e.stopImmediatePropagation(); window.AndroidBridge.openImagePicker(); return;
  }
  if (btn && ((btn.getAttribute("onclick") || "").includes("impFile") || ["Import", "ইম্পোর্ট"].includes((btn.textContent || "").trim()))) {
    e.preventDefault(); e.stopImmediatePropagation(); window.AndroidBridge.openJsonPicker(); return;
  }
  setTimeout(applyAvatar, 200);
}, true);
window.addEventListener("DOMContentLoaded", () => setTimeout(applyAvatar, 300));
</script></body>"""

html = html.replace("</body>", bridge, 1) if "</body>" in html else html + bridge
open(p, "w", encoding="utf-8").write(html)
print("CLEAN PATCH v2.1.0 OK!")
