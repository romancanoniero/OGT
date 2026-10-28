package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.shared.domain.BusinessCoupon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@Composable
fun CouponWallet() {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope();val owner=LocalOgtSession.current.me().id
    var coupons by remember(owner){mutableStateOf<List<BusinessCoupon>>(emptyList())}
    var code by remember(owner){mutableStateOf("")}
    var busy by remember(owner){mutableStateOf(false)}
    var error by remember(owner){mutableStateOf<String?>(null)}
    var loaded by remember(owner){mutableStateOf(false)}
    var message by remember(owner){mutableStateOf<String?>(null)}
    suspend fun refresh(){coupons=auth.feed.ownCoupons();loaded=true}
    LaunchedEffect(auth,owner){busy=true;try{refresh()}catch(cancel:CancellationException){throw cancel}catch(_:Exception){error="No se pudieron cargar tus cupones."}finally{busy=false}}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PrefCategory("Mis cupones","Los descuentos no cambian tus puntos de comunidad.")
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{Text(it);TextButton(enabled=!busy,onClick={scope.launch{busy=true;error=null;try{refresh()}catch(cancel:CancellationException){throw cancel}catch(_:Exception){error="No se pudieron cargar tus cupones."}finally{busy=false}}}){Text("Reintentar")}}
        if(loaded&&coupons.isEmpty())Text("Todavía no tenés cupones. Podés agregar uno con su código.")
        coupons.forEach{coupon ->
            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(coupon.companyName,style=MaterialTheme.typography.titleMedium)
                Text("${coupon.discountBps/100.0}% de descuento")
                SelectionContainer{Text(coupon.code)}
                val state=when(coupon.status){"ISSUED"->"Disponible";"REDEEMED"->"Utilizado";"REVOKED"->"Revocado";"EXPIRED"->"Vencido";else->coupon.status}
                val date=Instant.fromEpochMilliseconds(coupon.expiresAtEpochMs).toLocalDateTime(TimeZone.currentSystemDefault()).date
                Text("$state · Vence $date",style=MaterialTheme.typography.bodySmall)
            }}
        }
        OutlinedTextField(value=code,onValueChange={code=it;message=null},modifier=Modifier.fillMaxWidth(),singleLine=true,
            label={Text("Código de cupón")},keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters),enabled=!busy)
        Button(enabled=!busy&&code.isNotBlank(),onClick={scope.launch{busy=true;error=null;try{auth.feed.claimCoupon(code);code="";message="Cupón agregado.";refresh()}catch(cancel:CancellationException){throw cancel}catch(failure:Exception){error=failure.message?:"No se pudo agregar el cupón."}finally{busy=false}}}){Text("Agregar cupón")}
        message?.let{Text(it)}
    }
}
