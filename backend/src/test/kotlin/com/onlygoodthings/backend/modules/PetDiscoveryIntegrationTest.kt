package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.social.SocialSqlRepository
import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("business-integration")
class PetDiscoveryIntegrationTest {
 @Test fun alertsUseTheCrmRadiusAndManualAreaNeverStoredGpsWhileLinksAndFollowingRemainAvailable() {
  val url=System.getenv("OGT_BUSINESS_TEST_JDBC").orEmpty()
  require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:19432/ogt_business_qa_[a-z0-9]+")))
  Database(url,"ogt","ogt_qa_local_only").use { db ->
   val author=UUID.randomUUID().toString();val viewer=UUID.randomUUID().toString()
   db.withConnection { c ->for(id in listOf(author,viewer))c.prepareStatement("INSERT INTO users(id,firebase_uid,email,display_name,invite_code,home_location) VALUES(?::uuid,?,?,'Prueba Mascotas',?,ST_SetSRID(ST_MakePoint(-58.44,-34.62),4326))").use { q ->q.setString(1,id);q.setString(2,id);q.setString(3,"$id@example.test");q.setString(4,id);q.executeUpdate() } }
   val animals=AnimalSqlRepository(db);val posts=SocialSqlRepository(db);val settings=DiscoverySettingsRepository(db)
   val before=settings.get()
   try {
    fun publish(kind:String,lat:Double,lng:Double,owner:String=author)=animals.publish(owner,animalWriteFrom(mapOf("kind" to kind,"species" to "CAT","petName" to "Luna","description" to "Aviso QA","marks" to "Collar azul","lastSeenPlace" to "Zona QA","latitude" to lat,"longitude" to lng),null))
    val near=listOf("LOST","FOUND").map { publish(it,-34.60,-58.45) }
    val far=listOf("LOST","FOUND").map { publish(it,-31.42,-64.18) }
    val expanded=publish("FOUND",-34.92,-57.95);val own=publish("LOST",-31.42,-64.18,viewer)
    assertNull(settings.scope(viewer).latitude) // home_location contains an old GPS fix, not consent.
    assertTrue(posts.feed(viewer,FeedMode.HOME,200,null).filter { it.animalListing!=null }.all { it.authorId==viewer })
    assertTrue(animals.open(viewer).filter { isPetAlert(it.kind) }.all { it.reporterUserId==viewer })
    val gps=GeoPoint(-34.62,-58.44)
    assertEquals(gps.latitude,settings.scope(viewer,gps).latitude)
    for(row in near)assertTrue(posts.feed(viewer,FeedMode.HOME,200,null,device=gps).any { it.id==row.postId })
    settings.place(viewer,mapOf("place" to "Zona elegida a mano","latitude" to gps.latitude,"longitude" to gps.longitude))
    assertEquals(gps.latitude,settings.scope(viewer,GeoPoint(-31.42,-64.18)).latitude)
    val nearby=animals.open(viewer);for(row in near)assertTrue(nearby.any { it.listingId==row.listingId })
    assertFalse(nearby.any { it.listingId in far.map { it.listingId }+expanded.listingId });assertTrue(nearby.any { it.listingId==own.listingId })
    db.withConnection { c ->c.createStatement().use { q ->
     q.execute("INSERT INTO follows(follower_id,followed_id) VALUES('$viewer'::uuid,'$author'::uuid)")
     q.execute("""WITH added AS (
      INSERT INTO animal_listings(reporter_user_id,kind,species,urgency,title,description,location,alert_radius_m)
      SELECT '$author'::uuid,'FOUND','CAT','MEDIUM','Lejano QA','Aviso QA',ST_SetSRID(ST_MakePoint(-64.18,-31.42),4326),2000 FROM generate_series(1,205) RETURNING id,location
     ) INSERT INTO social_posts(author_kind,author_user_id,body,topic,listing_id,location,created_at)
       SELECT 'USER','$author'::uuid,'Aviso QA','Mascota encontrada',id,location,now()+interval '1 day' FROM added""")
    } }
    for(row in near)assertTrue(posts.feed(viewer,FeedMode.HOME,200,null).any { it.id==row.postId })
    for(row in near)assertTrue(animals.open(viewer).any { it.listingId==row.listingId })
    assertNotNull(posts.byId(far.first().postId,viewer));assertNotNull(animals.get(far.first().listingId))
    assertTrue(posts.feed(viewer,FeedMode.FOLLOWING,200,null).any { it.animalListing?.latitude == -31.42 })
    val wider=settings.save(viewer,100,settings.get().version)
    assertTrue(animals.open(viewer).any { it.listingId==expanded.listingId })
    assertTrue(posts.feed(viewer,FeedMode.HOME,200,null).any { it.id==expanded.postId })
    settings.save(viewer,20,wider.version)
    assertFalse(animals.open(viewer).any { it.listingId==expanded.listingId })
   } finally { settings.save(viewer,before.adoptionRadiusKm,settings.get().version) }
  }
 }
}
