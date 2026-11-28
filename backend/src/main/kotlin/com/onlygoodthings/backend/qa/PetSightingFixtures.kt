package com.onlygoodthings.backend.qa

import com.onlygoodthings.backend.community.AnimalContacts
import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.shared.domain.AnimalContactRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.security.MessageDigest
import com.onlygoodthings.backend.media.MediaStore
import com.onlygoodthings.backend.media.ImageOptimizer

@Serializable data class PetFixtureProfile(val key:String,val name:String,val nickname:String,val email:String,
    val photoUrl:String,val photoSource:String,val photoAuthor:String,val place:String,val latitude:Double,val longitude:Double,
    val photoFile:String?=null,val photoSha256:String?=null)
@Serializable data class PetFixtureObservation(val profileKey:String,val listingId:String,val note:String,
    val latitude:Double,val longitude:Double,val observedAtEpochMs:Long)
@Serializable data class PetFixtureBatch(val seedId:String,val ownerUserId:String,val importedAuthorId:String,
    val profiles:List<PetFixtureProfile>,val observations:List<PetFixtureObservation>)

fun petFixtureId(seed:String,key:String):String = UUID.nameUUIDFromBytes("ogt-pet-qa:$seed:$key".toByteArray()).toString()

/** Bounded, disclosed fixtures. The normal contact service owns conversations and retry rules. */
fun seedPetSightings(db:Database,batch:PetFixtureBatch,apply:Boolean):JsonObject {
    require(batch.seedId.matches(Regex("pet-sightings-[0-9]{8}")))
    UUID.fromString(batch.ownerUserId);UUID.fromString(batch.importedAuthorId)
    require(batch.profiles.size in 1..6 && batch.profiles.map { it.key }.distinct().size==batch.profiles.size)
    require(batch.observations.size in 1..12)
    require(batch.observations.map { it.profileKey to it.listingId }.distinct().size==batch.observations.size)
    val profiles=batch.profiles.associateBy { it.key }
    batch.profiles.forEach { p ->
        require(p.key.matches(Regex("[a-z]{2,20}")) && p.name.contains("QA"))
        require(p.nickname.matches(Regex("prueba_[a-z0-9_]{3,30}")) && p.email.endsWith("@example.test"))
        require(p.photoUrl.matches(Regex("https://onlygoodthings\\.lat/media/u/[0-9a-f-]{36}\\.jpg")))
        require(p.photoSource.startsWith("https://www.pexels.com/photo/") && p.photoAuthor.isNotBlank())
        require(p.place.isNotBlank() && p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0)
    }
    val contacts=AnimalContacts(db)
    db.withConnection { c ->
        c.prepareStatement("SELECT EXISTS(SELECT 1 FROM users WHERE id=?::uuid AND status='ACTIVE')").use { q ->
            q.setString(1,batch.ownerUserId);q.executeQuery().use { r ->r.next();require(r.getBoolean(1)) { "Fixture owner missing" } }
        }
        c.prepareStatement("SELECT EXISTS(SELECT 1 FROM users WHERE id=?::uuid AND publishing_blocked AND metadata->'testContact'->>'ownerUserId'=? AND metadata->'testContact'->>'scope'='SHARE_TEST')").use { q ->
            q.setString(1,batch.importedAuthorId);q.setString(2,batch.ownerUserId);q.executeQuery().use { r ->r.next();require(r.getBoolean(1)) { "Imported test author guard failed" } }
        }
        batch.observations.forEach { row ->
            require(row.profileKey in profiles && row.note.startsWith("PRUEBA FICTICIA"))
            com.onlygoodthings.shared.domain.validateAnimalContact(AnimalContactRequest(row.listingId,"SIGHTING",note=row.note,latitude=row.latitude,longitude=row.longitude,observedAtEpochMs=row.observedAtEpochMs),System.currentTimeMillis())
            c.prepareStatement("""SELECT EXISTS(SELECT 1 FROM animal_listings l JOIN social_posts p ON p.listing_id=l.id
                WHERE l.id=?::uuid AND l.reporter_user_id=?::uuid AND l.kind='LOST' AND NOT l.resolved
                AND p.moderation_status='VISIBLE' AND p.attribution_url LIKE 'https://www.instagram.com/patitas_perdidas9/%'
                AND ST_DWithin(l.location::geography,ST_SetSRID(ST_MakePoint(?,?),4326)::geography,1000))""").use { q ->
                q.setString(1,row.listingId);q.setString(2,batch.importedAuthorId);q.setDouble(3,row.longitude);q.setDouble(4,row.latitude)
                q.executeQuery().use { r ->r.next();require(r.getBoolean(1)) { "Only nearby observations of imported QA lost-pet notices are eligible" } }
            }
        }
        batch.profiles.forEach { p ->
            val id=petFixtureId(batch.seedId,p.key)
            c.prepareStatement("SELECT firebase_uid,email::text,display_name,nickname,photo_url,metadata->'petSightingFixture'->>'seedId' AS seed,metadata->'petSightingFixture'->>'ownerUserId' AS fixture_owner,role::text,status::text FROM users WHERE id=?::uuid OR firebase_uid=? OR nickname=? OR email=?::citext").use { q ->
                q.setString(1,id);q.setString(2,"ogt-test:pet:$id");q.setString(3,p.nickname);q.setString(4,p.email)
                q.executeQuery().use { r ->if(r.next()) {
                    require(r.getString("firebase_uid")=="ogt-test:pet:$id" && r.getString("seed")==batch.seedId && r.getString("fixture_owner")==batch.ownerUserId &&
                        r.getString("email")==p.email && r.getString("display_name")==p.name && r.getString("nickname")==p.nickname &&
                        r.getString("photo_url")==p.photoUrl && r.getString("role")=="USER" && r.getString("status")=="ACTIVE") { "Existing profile collision or modified fixture; no overwrite" }
                    require(!r.next())
                } }
            }
        }
    }
    // Verify existing report snapshots before adding anything. Never replace a previous sighting.
    batch.observations.forEach { row ->
        contacts.sightings(batch.importedAuthorId,row.listingId).firstOrNull { it.userId==petFixtureId(batch.seedId,row.profileKey) }?.let { previous ->
            require(previous.note==row.note && previous.observedAtEpochMs==row.observedAtEpochMs && previous.latitude==row.latitude && previous.longitude==row.longitude) { "Existing fixture report differs; no overwrite" }
        }
    }
    if(apply) {
        db.withConnection { c ->
            c.autoCommit=false
            try {
                batch.profiles.forEach { p ->
                    val id=petFixtureId(batch.seedId,p.key)
                    val metadata=buildJsonObject {
                        put("petSightingFixture",buildJsonObject { put("seedId",batch.seedId);put("ownerUserId",batch.ownerUserId);put("fictitious",true) })
                        put("bio","Perfil ficticio de QA. Foto ilustrativa de stock; la persona retratada no participa del avistamiento.")
                        put("settings",buildJsonObject { put("barrio",p.place);put("language","es");put("timeZone","America/Argentina/Buenos_Aires") })
                        put("photoCredit",buildJsonObject { put("source",p.photoSource);put("author",p.photoAuthor);put("license","https://www.pexels.com/license/") })
                    }
                    c.prepareStatement("""INSERT INTO users(id,firebase_uid,email,display_name,nickname,photo_url,invite_code,home_location,metadata)
                        VALUES(?::uuid,?,?::citext,?,?,?, ?,ST_SetSRID(ST_MakePoint(?,?),4326),?::jsonb) ON CONFLICT(id) DO NOTHING""").use { q ->
                        q.setString(1,id);q.setString(2,"ogt-test:pet:$id");q.setString(3,p.email);q.setString(4,p.name);q.setString(5,p.nickname);q.setString(6,p.photoUrl)
                        q.setString(7,"QA-PET-${id.take(8).uppercase()}");q.setDouble(8,p.longitude);q.setDouble(9,p.latitude);q.setString(10,metadata.toString());q.executeUpdate()
                    }
                }
                c.commit()
            }catch(e:Exception){c.rollback();throw e}finally{c.autoCommit=true}
        }
        batch.observations.forEach { row ->
            contacts.open(petFixtureId(batch.seedId,row.profileKey),AnimalContactRequest(row.listingId,"SIGHTING",note=row.note,
                latitude=row.latitude,longitude=row.longitude,observedAtEpochMs=row.observedAtEpochMs))
        }
    }
    return buildJsonObject {
        put("applied",apply);put("seedId",batch.seedId);put("profiles",batch.profiles.size);put("observations",batch.observations.size)
        put("listingIds",JsonArray(batch.observations.map { it.listingId }.distinct().map(::JsonPrimitive)))
    }
}

fun main(args:Array<String>) {
    require(args.size==2 && args[0] in setOf("--plan","--apply")) { "Usage: --plan|--apply reviewed-fixtures.json" }
    val manifest=Path.of(args[1]).toAbsolutePath()
    val batch=Json.decodeFromString<PetFixtureBatch>(Files.readString(manifest))
    val url=System.getenv("OGT_JDBC_URL") ?: error("Database URL required")
    val user=System.getenv("OGT_DB_USER") ?: "ogt";val password=System.getenv("OGT_DB_PASSWORD") ?: error("Database password required")
    Database(url,user,password).use { db ->
        seedPetSightings(db,batch,false)
        if(args[0]=="--apply") {
            val store=MediaStore(Path.of(System.getenv("OGT_MEDIA_DIR") ?: error("Media directory required")),"https://onlygoodthings.lat")
            batch.profiles.forEach { profile ->
                val file=requireNotNull(profile.photoFile);require(file.matches(Regex("[0-9]{1,12}\\.jpg")))
                val sha=requireNotNull(profile.photoSha256);require(sha.matches(Regex("[0-9a-f]{64}")))
                val path=manifest.parent.resolve("avatars").resolve(file)
                require(!Files.isSymbolicLink(path) && Files.isRegularFile(path))
                val bytes=Files.readAllBytes(path)
                fun hash(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it)}
                require(hash(bytes)==sha)
                val id=UUID.nameUUIDFromBytes("ogt-pet-qa:avatar:$sha".toByteArray()).toString()
                require(profile.photoUrl==store.publicUrl("$id.jpg"))
                val optimized=ImageOptimizer.optimize(bytes,"jpg")
                val existing=store.resolvePublic("$id.jpg")
                if(existing!=null)require(hash(Files.readAllBytes(existing))==hash(optimized.bytes)) { "Existing avatar differs; no overwrite" }
                else require(store.save(bytes,file,"image/jpeg",id).url==profile.photoUrl)
            }
        }
        println(seedPetSightings(db,batch,args[0]=="--apply"))
    }
}
