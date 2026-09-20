package com.onlygoodthings.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WsFrameType {
    @SerialName("CONNECT_AUTH")
    CONNECT_AUTH,

    @SerialName("PARKING_BROADCAST")
    PARKING_BROADCAST,

    @SerialName("PARKING_CLAIM")
    PARKING_CLAIM,

    @SerialName("LOCATION_TICK")
    LOCATION_TICK,

    @SerialName("IMPACT_ALERT")
    IMPACT_ALERT,

    @SerialName("HEARTBEAT")
    HEARTBEAT,

    @SerialName("ACK")
    ACK,

    @SerialName("ERROR")
    ERROR,
}

@Serializable
enum class WsChannel {
    @SerialName("parking")
    PARKING,

    @SerialName("social")
    SOCIAL,

    @SerialName("animals")
    ANIMALS,

    @SerialName("timebank")
    TIMEBANK,

    @SerialName("csr")
    CSR,

    @SerialName("crowdfunding")
    CROWDFUNDING,
}
