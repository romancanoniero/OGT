package com.onlygoodthings.shared.domain

import kotlin.test.*

class AccountSettingsTest {
    @Test fun quietHoursRespectBothBoundariesAndTheOvernightWindow() {
        val settings=AccountSettings()
        for(hour in 0..23)assertEquals(hour in 7..21,settings.noticePresentation("MENTION",hour).sound,"hour=$hour")
        assertTrue(settings.copy(quietHoursEnabled=false).noticePresentation("MENTION",23).sound)
    }
    @Test fun categoriesAreIndependentAndRadarChangesSoundWithoutDroppingTheNotice() {
        val settings=AccountSettings(animalAlertPush=false,skillAlertPush=false,parkingRadarSounds=false)
        for(kind in listOf("ALERT","ANIMAL_ALERT","LOST_PET","SIGHTING","MATCH","TIMEBANK_MESSAGE","SKILL"))assertFalse(settings.noticePresentation(kind,12).deliver)
        assertEquals(NoticePresentation(true,false),settings.noticePresentation("PARKING_FOUND",12))
        assertEquals(NoticePresentation(true,true),settings.noticePresentation("MENTION",12))
        assertEquals(NoticePresentation(true,true),settings.noticePresentation("POINTS",12))
    }
    @Test fun goalsAdvanceAcrossTheActualLevelThresholdsWithoutInventingAnotherLevel() {
        listOf(0 to 500,499 to 500,500 to 1500,1499 to 1500,1500 to 3000,2999 to 3000).forEach { (points,goal)->assertEquals(goal,nextCommunityGoal(points)) }
        assertNull(nextCommunityGoal(3000));assertNull(nextCommunityGoal(10000))
    }
}
