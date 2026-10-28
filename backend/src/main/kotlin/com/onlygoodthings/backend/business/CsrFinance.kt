package com.onlygoodthings.backend.business

fun CsrRepository.finance(actor:String,company:String):CsrFinanceSummary = db.withConnection { c ->
    val previous=c.transactionIsolation
    c.transactionIsolation=java.sql.Connection.TRANSACTION_REPEATABLE_READ
    c.autoCommit=false
    try {
    val owner=access(c,actor,company,financeRoles+setOf("ANALYST"))
    val campaigns=c.prepareStatement("SELECT coalesce(sum(reserved_cents),0),coalesce(sum(spent_cents),0) FROM campaigns WHERE company_id=?::uuid").use { q ->
        q.setString(1,company);q.executeQuery().use { r ->r.next();r.getLong(1) to r.getLong(2) }
    }
    val totals=c.prepareStatement("SELECT coalesce(sum(available_delta_cents),0),coalesce(sum(reserved_delta_cents),0),coalesce(sum(spent_delta_cents),0),coalesce(sum(debt_delta_cents),0) FROM csr_ledger WHERE company_id=?::uuid").use { q ->
        q.setString(1,company);q.executeQuery().use { r ->r.next();listOf(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4)) }
    }
    val entries=c.prepareStatement("SELECT id::text,kind,available_delta_cents,reserved_delta_cents,spent_delta_cents,debt_delta_cents,note,(extract(epoch FROM created_at)*1000)::bigint FROM csr_ledger WHERE company_id=?::uuid ORDER BY created_at DESC,id DESC LIMIT 100").use { q ->
        q.setString(1,company);q.executeQuery().use { r ->buildList { while(r.next())add(CsrLedgerEntry(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5),r.getLong(6),r.getString(7),r.getLong(8))) } }
    }
    val extra=c.prepareStatement("SELECT impact_score,(SELECT count(*) FROM csr_payment_incidents i JOIN csr_payment_orders o ON o.id=i.order_id WHERE o.company_id=c.id AND i.resolved_at IS NULL) FROM companies c WHERE id=?::uuid").use{q ->q.setString(1,company);q.executeQuery().use{r ->r.next();r.getDouble(1) to r.getInt(2)}}
    val result=CsrFinanceSummary(company,owner.available,campaigns.first,campaigns.second,owner.debt,
        totals==listOf(owner.available,campaigns.first,campaigns.second,owner.debt),entries,extra.first,extra.second)
    c.commit();result
    }catch(error:Exception){c.rollback();throw error}finally{c.autoCommit=true;c.transactionIsolation=previous}
}

fun CsrRepository.fundingCandidates(actor:String,company:String):List<CsrFundingCandidate> = db.withConnection{c ->
    access(c,actor,company,financeRoles+setOf("ANALYST"))
    c.prepareStatement("""SELECT o.id::text,o.kind::text,u.display_name,(extract(epoch FROM o.verified_at)*1000)::bigint
        FROM action_outcomes o JOIN users u ON u.id=o.actor_user_id WHERE o.company_id=?::uuid AND o.verified
        AND NOT EXISTS(SELECT 1 FROM csr_action_funding f WHERE f.outcome_id=o.id) ORDER BY o.verified_at DESC LIMIT 200""").use{q ->q.setString(1,company);q.executeQuery().use{r ->buildList{while(r.next())add(CsrFundingCandidate(r.getString(1),r.getString(2),r.getString(3),r.getLong(4)))}}}
}
