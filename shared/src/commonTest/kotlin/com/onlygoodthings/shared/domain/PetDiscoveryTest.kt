package com.onlygoodthings.shared.domain
import kotlin.test.*

class PetDiscoveryTest {
 @Test fun revokedPermissionDisabledGpsAndOldFixesNeverSupplyADeviceReference() {
  val fix=GeoPoint(-34.62,-58.44)
  assertNull(PetDiscovery.device(false,fix,1000,1001))
  assertNull(PetDiscovery.device(true,fix,1000,121001))
  assertNull(PetDiscovery.device(true,fix,2000,1000))
  assertNull(PetDiscovery.device(true,null,1000,1001))
  assertEquals(fix,PetDiscovery.device(true,fix,1000,1001))
 }
 @Test fun locationPolicyIncludesAllAnimalNoticesButLeavesOrdinaryPostsAlone() {
  for(kind in listOf("ADOPTION","LOST","FOUND"))assertTrue(PetDiscovery.requiresLocation(kind))
  assertFalse(PetDiscovery.requiresLocation(null,"Merienda"))
 }
 @Test fun manualAreaWinsAndAnAbsentDeviceFixNeverBecomesAnOldGpsOrSeedPoint() {
  val manual=GeoPoint(-34.62,-58.44);val gps=GeoPoint(-31.42,-64.18)
  assertEquals(manual,PetDiscovery.reference(manual,gps));assertEquals(manual,PetDiscovery.reference(manual,null))
  assertEquals(gps,PetDiscovery.reference(null,gps));assertNull(PetDiscovery.reference(null,null))
  assertFalse(PetDiscovery.allows(false,null,manual));assertTrue(PetDiscovery.allows(true,null,manual))
 }
}
