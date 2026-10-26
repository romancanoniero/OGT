"""Regresiones del arranque, publicación y avatar persistente de OGT móvil."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "composeApp/src/commonMain/kotlin/com/onlygoodthings/app"
SHARED = ROOT / "shared/src/commonMain/kotlin/com/onlygoodthings/shared"


class MobileProductionContractTest(unittest.TestCase):
    def read(self, path):
        return path.read_text(encoding="utf-8")

    def test_landing_order_and_animated_splash(self):
        landing = self.read(APP / "ui/screens/LandingScreen.kt")
        splash = self.read(APP / "ui/screens/SplashScreen.kt")
        self.assertNotIn("Only Good Things (OGT)", landing)
        self.assertLess(landing.index('"Only Good Things"'),
                        landing.index('"RED SOLIDARIA DE COMUNIDAD"'))
        self.assertLess(splash.index("right.animateTo(1f"),
                        splash.index("left.animateTo(1f"))
        self.assertLess(splash.index("left.animateTo(1f"),
                        splash.index("dot.animateTo(1f"))
        self.assertIn("onContinue()", splash)

    def test_valid_session_skips_marketing_landing(self):
        app = self.read(APP / "App.kt")
        self.assertIn("AuthRestore.LoggedOut -> OgtRoute.Landing", app)
        self.assertIn("AuthRestore.FirstRun, AuthRestore.Home ->", app)
        self.assertIn("applyHonorRoute(OgtRoute.Feed)", app)
        self.assertIn("OgtRoute.Landing -> LandingScreen(", app)
        controller = self.read(APP / "auth/AuthController.kt")
        self.assertNotIn("runCatching { finishSession(user) }", controller)
        self.assertIn("AuthRestore.Home", controller)

    def test_post_is_created_remotely_before_being_shown(self):
        editor = self.read(APP / "ui/screens/PublishActionScreen.kt")
        self.assertNotIn('val postId = "post-pub-$now"', editor)
        self.assertLess(editor.index("val remote = auth.feed.publishPost("),
                        editor.index("db.upsertRemoteSocial(remote)",
                                     editor.index("val remote = auth.feed.publishPost(")))
        self.assertIn("auth.uploadPublicationImage(item.path)", editor)
        self.assertIn("publishError = failure.message", editor)
        db = self.read(SHARED / "data/local/OgtLocalDatabase.kt")
        self.assertIn("isServerBackedPostId(post.id)", db)
        store = self.read(APP / "data/OgtStore.kt")
        self.assertIn("verifiedOnly = true", store)
        crm = self.read(ROOT / "backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt")
        self.assertIn("WHERE p.moderation_status = 'VISIBLE'", crm)

    def test_avatar_is_uploaded_and_persisted_before_local_change(self):
        settings = self.read(APP / "ui/screens/SettingsScreen.kt")
        auth = self.read(APP / "auth/AuthController.kt")
        api = self.read(APP / "auth/AuthSessionApi.kt")
        self.assertIn("auth.saveMyProfilePhoto(picked.path)", settings)
        self.assertNotIn("session.applyProfilePhoto(picked.path)", settings)
        self.assertLess(auth.index("val saved = api.saveProfilePhoto(path"),
                        auth.index("preview.applyProfilePhoto(saved)"))
        self.assertIn("/api/v1/media/upload", api)
        self.assertIn("/api/v1/users/profile/update", api)
        self.assertLess(api.index("/api/v1/media/upload"),
                        api.index("/api/v1/users/profile/update"))
        self.assertIn("profile?.photoUrl?.takeIf", auth)


if __name__ == "__main__":
    unittest.main()
