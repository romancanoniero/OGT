package com.onlygoodthings.backend.modules

import com.onlygoodthings.backend.infra.Database
import com.onlygoodthings.backend.http.*
import com.onlygoodthings.shared.domain.DiscoverySettings
import com.onlygoodthings.shared.domain.FeedDiscoveryScope
import com.onlygoodthings.shared.domain.AdoptionProximity
import com.onlygoodthings.shared.domain.GeoPoint
import com.onlygoodthings.shared.domain.PetDiscovery

internal fun discoveryDevicePoint(data:Map<String,Any?>):GeoPoint? {
    val lat=data.optDouble("latitude");val lng=data.optDouble("longitude")
    require((lat==null)==(lng==null)) { "Indicá ambas coordenadas de la ubicación actual." }
    if(lat==null)return null
    return AdoptionProximity.point(lat,lng) ?: throw IllegalArgumentException("Ubicación actual inválida.")
}

class DiscoverySettingsRepository(private val db:Database) {
    fun get():DiscoverySettings=db.withConnection { c ->c.createStatement().use { q ->q.executeQuery("SELECT adoption_radius_km,version FROM discovery_settings WHERE singleton").use { r ->
        if(r.next())DiscoverySettings(r.getInt(1),r.getLong(2)) else DiscoverySettings()
    } } }
    fun save(actor:String,radius:Int,version:Long):DiscoverySettings {
        require(radius in 1..500) { "Indicá una distancia entre 1 y 500 km." }
        return db.withConnection { c ->
            c.autoCommit=false
            try {
                val saved=c.prepareStatement("UPDATE discovery_settings SET adoption_radius_km=?,version=version+1,updated_by=?::uuid,updated_at=now() WHERE singleton AND version=? RETURNING adoption_radius_km,version").use { q ->
                    q.setInt(1,radius);q.setString(2,actor);q.setLong(3,version);q.executeQuery().use { r ->
                        require(r.next()) { "La configuración cambió. Actualizá antes de guardar." };DiscoverySettings(r.getInt(1),r.getLong(2))
                    }
                }
                c.prepareStatement("INSERT INTO crm_audit_events(actor_user_id,action,target_type,target_id,reason,outcome) VALUES(?::uuid,'DISCOVERY_SETTINGS_SAVE','discovery_settings','global',?,'SUCCESS')").use { q ->
                    q.setString(1,actor);q.setString(2,"Descubrimiento de mascotas: $radius km; versión ${saved.version}");q.executeUpdate()
                }
                c.commit();saved
            } catch(e:Exception) { c.rollback();throw e } finally { c.autoCommit=true }
        }
    }

    fun scope(user:String,device:GeoPoint?=null):FeedDiscoveryScope {
        val settings=get()
        return db.withConnection { c ->c.prepareStatement("SELECT ST_Y(discovery_location),ST_X(discovery_location),discovery_place FROM users WHERE id=?::uuid").use { q ->
            q.setString(1,user);q.executeQuery().use { r ->
                if(!r.next()) FeedDiscoveryScope(settings=settings) else {
                    val lat=r.getObject(1) as? Number;val lng=r.getObject(2) as? Number
                    val manual=AdoptionProximity.point(lat?.toDouble(),lng?.toDouble())
                    val point=PetDiscovery.reference(manual,device)
                    FeedDiscoveryScope(point?.latitude,point?.longitude,settings,r.getString(3)?.takeIf { manual!=null })
                }
            }
        } }
    }
    fun place(user:String,data:Map<String,Any?>):FeedDiscoveryScope {
        val label=data.reqString("place").trim();val lat=data.reqDouble("latitude");val lng=data.reqDouble("longitude")
        require(label.isNotBlank()&&label.length<=200&&AdoptionProximity.point(lat,lng)!=null) { "Elegí una zona confirmada de las sugerencias." }
        db.withConnection { c ->c.prepareStatement("UPDATE users SET discovery_location=ST_SetSRID(ST_MakePoint(?,?),4326),discovery_place=? WHERE id=?::uuid").use { q ->
            q.setDouble(1,lng);q.setDouble(2,lat);q.setString(3,label);q.setString(4,user);q.executeUpdate()
        } }
        return scope(user)
    }
}
