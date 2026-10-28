package com.onlygoodthings.backend.business

import kotlinx.serialization.json.*
import java.util.UUID

private val couponFields="""p.id::text,p.campaign_id::text,p.company_id::text,coalesce(c.trade_name,c.legal_name),p.code,
    CASE WHEN p.status='ISSUED' AND p.expires_at<=now() THEN 'EXPIRED' ELSE p.status::text END,
    p.discount_bps,p.issued_to_user::text,(extract(epoch FROM p.expires_at)*1000)::bigint,
    (extract(epoch FROM p.redeemed_at)*1000)::bigint"""
private fun java.sql.ResultSet.coupon()=CsrCoupon(getString(1),getString(2),getString(3),getString(4),getString(5),getString(6),getInt(7),getString(8),getLong(9),getLong(10).let {if(wasNull())null else it})

fun CsrRepository.issueCoupon(actor:String,company:String,request:String,campaignId:String,discount:Int,recipient:String?,note:String):CsrActionResult {
    require(discount in 100..10000 && note.length<=500){ "El descuento debe ser entre 1% y 100%; la nota admite 500 caracteres" }
    recipient?.let { UUID.fromString(it) }
    return operation(actor,company,request,"COUPON_ISSUE",buildJsonObject {put("campaign",campaignId);put("discount",discount);put("recipient",recipient);put("note",note)},campaignRoles) { c,owner ->
        require(owner.verified){ "La empresa debe estar verificada" }
        val campaign=campaign(c,company,campaignId)
        require(campaign.status=="ACTIVE" && campaign.socialProgress>=campaign.socialGoal){ "La campaña debe estar activa y su meta verificada" }
        if(recipient!=null)c.prepareStatement("SELECT 1 FROM users WHERE id=?::uuid AND status='ACTIVE'").use { q ->q.setString(1,recipient);q.executeQuery().use {require(it.next()){ "Destinatario no disponible" }} }
        val id=UUID.randomUUID().toString();val code="OGT-"+UUID.randomUUID().toString().replace("-","").uppercase()
        c.prepareStatement("INSERT INTO promo_codes(id,campaign_id,company_id,code,discount_bps,issued_to_user,expires_at,condition_note) VALUES(?::uuid,?::uuid,?::uuid,?,?,?::uuid,now()+interval '30 days',?)").use { q ->
            q.setString(1,id);q.setString(2,campaignId);q.setString(3,company);q.setString(4,code);q.setInt(5,discount);q.setString(6,recipient);q.setString(7,note);q.executeUpdate()
        }
        CsrActionResult(id,"ISSUED",code)
    }
}
fun CsrRepository.companyCoupons(actor:String,company:String):List<CsrCoupon> = db.withConnection { c ->
    access(c,actor,company,campaignRoles+financeRoles)
    c.prepareStatement("SELECT $couponFields FROM promo_codes p JOIN companies c ON c.id=p.company_id WHERE p.company_id=?::uuid ORDER BY p.created_at DESC LIMIT 200").use { q ->q.setString(1,company);q.executeQuery().use { r ->buildList {while(r.next())add(r.coupon())} } }
}
fun CsrRepository.ownCoupons(actor:String):List<CsrCoupon> = db.withConnection { c ->ownCoupons(c,actor)}
internal fun CsrRepository.ownCoupons(c:java.sql.Connection,actor:String):List<CsrCoupon> {
    return c.prepareStatement("SELECT $couponFields FROM promo_codes p JOIN companies c ON c.id=p.company_id WHERE p.issued_to_user=?::uuid ORDER BY p.created_at DESC LIMIT 200").use { q ->q.setString(1,actor);q.executeQuery().use { r ->buildList {while(r.next())add(r.coupon())} } }
}
fun CsrRepository.claimCoupon(actor:String,rawCode:String):CsrActionResult {
    val code=rawCode.trim().uppercase();require(code.matches(Regex("[A-Z0-9-]{6,60}"))){ "Código inválido" }
    return db.withConnection { c ->
        c.autoCommit=false
        try {
            val row=c.prepareStatement("SELECT p.id::text,p.issued_to_user::text FROM promo_codes p JOIN campaigns a ON a.id=p.campaign_id WHERE p.code=? AND p.status='ISSUED' AND p.expires_at>now() AND a.status<>'CANCELLED' FOR UPDATE OF p").use { q ->q.setString(1,code);q.executeQuery().use { r ->require(r.next()){ "Cupón no disponible" };r.getString(1) to r.getString(2) } }
            require(row.second==null || row.second==actor){ "Cupón no disponible" }
            c.prepareStatement("UPDATE promo_codes SET issued_to_user=?::uuid WHERE id=?::uuid").use { q ->q.setString(1,actor);q.setString(2,row.first);q.executeUpdate() }
            c.commit();CsrActionResult(row.first,"ISSUED",code)
        }catch(error:Exception){c.rollback();throw error}finally{c.autoCommit=true}
    }
}
fun CsrRepository.changeCoupon(actor:String,company:String,request:String,rawCode:String,redeem:Boolean):CsrActionResult {
    val code=rawCode.trim().uppercase();require(code.matches(Regex("[A-Z0-9-]{6,60}")))
    return operation(actor,company,request,if(redeem)"COUPON_REDEEM" else "COUPON_REVOKE",buildJsonObject {put("code",code)},campaignRoles+financeRoles) { c,owner ->
        require(owner.verified){ "La empresa debe estar verificada" }
        val coupon=c.prepareStatement("SELECT $couponFields FROM promo_codes p JOIN companies c ON c.id=p.company_id WHERE p.company_id=?::uuid AND p.code=? FOR UPDATE OF p").use { q ->q.setString(1,company);q.setString(2,code);q.executeQuery().use { r ->require(r.next()){ "Cupón no disponible para esta empresa" };r.coupon() } }
        require(coupon.status=="ISSUED"){ "El cupón ya fue utilizado, revocado o venció" }
        val state=if(redeem)"REDEEMED" else "REVOKED"
        c.prepareStatement("UPDATE promo_codes SET status=?::promo_code_status,redeemed_at=CASE WHEN ? THEN now() ELSE redeemed_at END,redeemed_by_user=CASE WHEN ? THEN ?::uuid ELSE redeemed_by_user END WHERE id=?::uuid").use { q ->
            q.setString(1,state);q.setBoolean(2,redeem);q.setBoolean(3,redeem);q.setString(4,actor);q.setString(5,coupon.id);q.executeUpdate()
        }
        CsrActionResult(coupon.id,state,code)
    }
}
