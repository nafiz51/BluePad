package com.bluepad.ui

import com.bluepad.ui.BluetoothOnboarding.Action
import org.junit.Assert.*
import org.junit.Test

class BluetoothOnboardingTest {
    @Test fun returningUserIsReadyWithoutPrompt() { assertEquals(Action.READY,BluetoothOnboarding().next(true,true,true,false)) }
    @Test fun firstPermissionRequestIsSingleAndContinuesToEnable() {
        val p=BluetoothOnboarding()
        assertEquals(Action.PERMISSION,p.next(false,false,true,false))
        assertEquals(Action.NONE,p.next(false,false,true,false))
        p.complete(true); assertEquals(Action.ENABLE,p.next(true,false,true,false))
        p.complete(true); assertEquals(Action.READY,p.next(true,true,true,false))
    }
    @Test fun denialBlocksAutomaticPromptsIncludingReopeningAndRestart() {
        val p=BluetoothOnboarding(); p.next(false,false,true,false); p.complete(false)
        repeat(20) { assertEquals(Action.NONE,p.next(false,false,true,false)) }
        assertEquals(Action.NONE,BluetoothOnboarding(p.blocked).next(false,false,true,false))
        assertEquals(Action.PERMISSION,p.next(false,false,true,false,manual=true))
    }
    @Test fun permanentDenialRequiresExplicitSettingsAction() {
        val p=BluetoothOnboarding()
        assertEquals(Action.NONE,p.next(false,false,true,true))
        assertEquals(Action.APP_SETTINGS,p.next(false,false,true,true,manual=true))
        p.complete(false); assertEquals(Action.NONE,p.next(false,false,true,true))
    }
    @Test fun settingsGrantContinuesAndSettingsWithoutGrantDoesNotLoop() {
        val p=BluetoothOnboarding(true); p.next(false,false,true,true,true); p.complete(true)
        assertEquals(Action.ENABLE,p.next(true,false,true,false))
        p.complete(false); assertEquals(Action.NONE,p.next(true,false,true,false))
        assertEquals(Action.READY,p.next(true,true,true,false))
    }
    @Test fun cancellationRequiresManualEnableRetry() {
        val p=BluetoothOnboarding(); assertEquals(Action.ENABLE,p.next(true,false,true,false))
        p.complete(false); assertEquals(Action.NONE,p.next(true,false,true,false))
        assertEquals(Action.ENABLE,p.next(true,false,true,false,true))
    }
    @Test fun rotationRestoresOutstandingRequestWithoutDuplicate() {
        for(action in listOf(Action.PERMISSION,Action.ENABLE,Action.APP_SETTINGS)) {
            val restored=BluetoothOnboarding(false,Action.valueOf(action.name))
            assertEquals(Action.NONE,restored.next(false,false,true,false,true))
            restored.complete(true); assertEquals(Action.READY,restored.next(true,true,true,false))
        }
    }
    @Test fun preAndroidTwelveNeedsOnlyEnableAndMissingHardwareDoesNotPrompt() {
        assertEquals(Action.ENABLE,BluetoothOnboarding().next(true,false,true,false))
        assertEquals(Action.NONE,BluetoothOnboarding().next(false,false,false,false,true))
    }
}
