package com.akira.island.face

import org.junit.Assert.*
import org.junit.Test

class FaceWakeSessionTest {
    private fun asleep() = FaceWakeSession().apply { screenOff(enabled=true,secure=true,now=100) }

    @Test fun fastUnlockSurvivesScreenOnBeforePresentAndDelayedCallbacks() {
        val wake=asleep();val s=wake.session
        wake.refresh(enabled=true,knownLocked=false,now=300)
        s.userPresent(350,true)
        // A refresh immediately before binding must keep both the epoch and USER_PRESENT.
        wake.refresh(enabled=true,knownLocked=false,now=400)
        s.signal(FaceSignal.ACCEPTED,200,450);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,500)
        s.tick(900);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun presentAndCallbackBeforeScreenOnKeepTheSameCandidateEpoch() {
        val wake=asleep();val s=wake.session
        s.userPresent(300,true)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,350);s.signal(FaceSignal.ACCEPTED,200,400)
        wake.refresh(enabled=true,knownLocked=false,now=450)
        s.tick(800);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun callbackBeforeWakeAndPresentIsAlsoPairedWithTheSameLock() {
        val wake=asleep();val s=wake.session
        s.signal(FaceSignal.ACCEPTED,200,200);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,250)
        wake.refresh(enabled=true,knownLocked=false,now=300)
        s.userPresent(350,true);s.tick(750);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun repeatedLockedRefreshDoesNotResetAPendingFaceOrEntryProof() {
        val wake=FaceWakeSession();val s=wake.session
        wake.refresh(enabled=true,knownLocked=true,now=100)
        s.signal(FaceSignal.ACCEPTED,200,200);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,250)
        wake.refresh(enabled=true,knownLocked=true,now=300)
        s.userPresent(350,true);s.tick(750);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun wakeAndPresentWithEitherMissingPositiveProofNeverBecomeSuccessOrScanning() {
        for(missing in listOf(FaceSignal.ACCEPTED,FaceSignal.NON_CREDENTIAL_ENTRY)) {
            val wake=asleep();val s=wake.session
            wake.refresh(enabled=true,knownLocked=false,now=300);s.userPresent(350,true)
            if(missing!=FaceSignal.ACCEPTED)s.signal(FaceSignal.ACCEPTED,200,400)
            if(missing!=FaceSignal.NON_CREDENTIAL_ENTRY)s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,450)
            assertEquals(FacePhase.HIDDEN,s.phase)
            s.tick(1700);assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
        }
    }
    @Test fun aNewScreenOffDropsOldGreenPendingProofsAndPreviousEpochCallbacks() {
        val wake=asleep();val s=wake.session
        s.signal(FaceSignal.ACCEPTED,200,200);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,250)
        s.userPresent(300,true);s.tick(700);assertEquals(FacePhase.SUCCESS,s.phase)
        wake.screenOff(enabled=true,secure=true,now=1000)
        assertEquals(FacePhase.HIDDEN,s.phase)
        wake.refresh(enabled=true,knownLocked=false,now=1100)
        s.signal(FaceSignal.ACCEPTED,200,1150);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,1150)
        s.userPresent(1200,true);s.tick(1600)
        assertEquals(FacePhase.HIDDEN,s.phase)
    }
    @Test fun disablingThenEnablingAfterUnlockCannotReplayTheOldCandidate() {
        val wake=asleep();val s=wake.session
        s.signal(FaceSignal.ACCEPTED,200,200);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,250)
        wake.refresh(enabled=false,knownLocked=false,now=300)
        wake.refresh(enabled=true,knownLocked=false,now=350)
        s.signal(FaceSignal.ACCEPTED,200,400);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,400)
        s.userPresent(450,true);s.tick(850)
        assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
    }
    @Test fun sourceLossOrStopClearsTheCandidateBeforeAReconnectionAfterUnlock() {
        val wake=asleep();val s=wake.session
        s.signal(FaceSignal.ACCEPTED,200,200);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,250)
        wake.clear();wake.refresh(enabled=true,knownLocked=false,now=300)
        s.userPresent(350,true);s.signal(FaceSignal.ACCEPTED,200,400)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,400);s.tick(800)
        assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
    }
    @Test fun anInsecureOrDisabledScreenOffDoesNotCreateACandidate() {
        for((enabled,secure) in listOf(false to true,true to false)) {
            val wake=FaceWakeSession();val s=wake.session
            wake.screenOff(enabled,secure,100)
            wake.refresh(enabled=true,knownLocked=false,now=300)
            s.signal(FaceSignal.ACCEPTED,200,400);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,250,400)
            s.userPresent(450,true);s.tick(850)
            assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
        }
    }
}
