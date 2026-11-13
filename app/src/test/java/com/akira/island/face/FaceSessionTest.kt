package com.akira.island.face

import org.junit.Assert.*
import org.junit.Test

class FaceSessionTest {
    @Test fun aVerifiedScanContinuesSmoothlyUntilDeviceEntryIsConfirmed() {
        val s=FaceSession().apply{lock(0)}
        s.signal(FaceSignal.START,10,10);s.signal(FaceSignal.ACCEPTED,100,100)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150);s.userPresent(200,true)
        assertEquals(FacePhase.SCANNING,s.phase);assertEquals(600L,s.deadline)
        s.tick(600);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    private fun ready() = FaceSession().apply { lock(0) }
    @Test fun faceAndVerifiedPresenceStillNeedPositiveNonCredentialEntry() {
        val s=ready(); s.signal(FaceSignal.ACCEPTED,100,100)
        s.tick(600); assertEquals(FacePhase.HIDDEN,s.phase)
        s.userPresent(700,true); assertEquals(FacePhase.HIDDEN,s.phase)
        s.tick(1100); assertEquals(FacePhase.HIDDEN,s.phase)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,650,1100)
        assertEquals(1500L,s.deadline);s.tick(1500);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun userPresentAloneNeverBecomesFace() {
        val s=ready(); s.userPresent(100,true); s.tick(1400)
        assertEquals(FacePhase.HIDDEN,s.phase); assertNull(s.deadline)
    }
    @Test fun resultCanArriveAfterTheBroadcastWithinTheSameSession() {
        val s=ready(); s.userPresent(200,true);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,180,250)
        s.signal(FaceSignal.ACCEPTED,100,300)
        s.tick(600); assertEquals(FacePhase.HIDDEN,s.phase)
        s.tick(700); assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun delayedCatchUpStillWaitsForCompetingUnlockEvidence() {
        val s=ready();s.userPresent(200,true);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,180,800)
        s.signal(FaceSignal.ACCEPTED,100,900)
        assertEquals(FacePhase.HIDDEN,s.phase);assertEquals(1300L,s.deadline)
        s.signal(FaceSignal.OTHER_UNLOCK,180,950);s.tick(1400)
        assertEquals(FacePhase.HIDDEN,s.phase)
    }
    @Test fun fingerprintOrCredentialVetoesFaceBeforeAndAfterDisplay() {
        for(at in listOf(300L,800L)) {
            val s=ready();s.signal(FaceSignal.ACCEPTED,100,100)
            s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150);s.userPresent(200,true)
            s.tick(600);assertEquals(FacePhase.SUCCESS,s.phase)
            s.signal(FaceSignal.OTHER_UNLOCK,at,800);s.tick(900)
            assertEquals(FacePhase.HIDDEN,s.phase); assertNull(s.deadline)
            s.signal(FaceSignal.ACCEPTED,900,900); s.userPresent(950,true); s.tick(1400)
            assertEquals(FacePhase.HIDDEN,s.phase)
        }
    }
    @Test fun delayedSameSessionVetoStillWithdrawsAnAlreadyDisplayedSuccess() {
        val s=ready();s.signal(FaceSignal.ACCEPTED,100,100)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150);s.userPresent(200,true)
        s.tick(600);assertEquals(FacePhase.SUCCESS,s.phase)
        s.signal(FaceSignal.OTHER_UNLOCK,180,1500)
        assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
    }
    @Test fun previousSessionVetoCannotWithdrawANewSuccessEvenWhenDeliveredLate() {
        for(at in listOf(180L,900L)) {
            val s=ready();s.lock(1000)
            s.signal(FaceSignal.ACCEPTED,1100,1100);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,1150,1150)
            s.userPresent(1200,true);s.tick(1600)
            assertEquals(FacePhase.SUCCESS,s.phase)
            s.signal(FaceSignal.OTHER_UNLOCK,at,1700)
            assertEquals(FacePhase.SUCCESS,s.phase)
        }
    }
    @Test fun oldFutureAndDelayedResultsAreIgnored() {
        for(at in listOf(9L,2200L,20L)) {
            val s=FaceSession().apply{lock(10)}
            val now=if(at==20L) 1800L else 2000L
            s.signal(FaceSignal.ACCEPTED,at,now);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,now-50,now)
            s.userPresent(now,true);s.tick(now+500)
            assertEquals(FacePhase.HIDDEN,s.phase)
        }
    }
    @Test fun missingSessionAndStillLockedDeviceNeverAccept() {
        val noSession=FaceSession();noSession.signal(FaceSignal.ACCEPTED,100,100)
        noSession.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150);noSession.userPresent(200,true);noSession.tick(600)
        assertEquals(FacePhase.HIDDEN,noSession.phase)
        val s=ready();s.signal(FaceSignal.ACCEPTED,100,100);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150)
        s.userPresent(200,false);s.tick(600)
        assertEquals(FacePhase.HIDDEN,s.phase)
    }
    @Test fun rejectionAndCancellationCannotCreateSuccess() {
        val s=ready(); s.signal(FaceSignal.START,10,10); assertEquals(FacePhase.SCANNING,s.phase)
        s.signal(FaceSignal.REJECTED,100,100); assertEquals(FacePhase.FAILURE,s.phase)
        s.userPresent(200,true); s.tick(1500); assertEquals(FacePhase.HIDDEN,s.phase)
        s.lock(2000); s.signal(FaceSignal.START,2010,2010); s.signal(FaceSignal.STOPPED,2020,2020)
        assertEquals(FacePhase.HIDDEN,s.phase)
    }
    @Test fun normalStopAfterSuccessDoesNotEraseTheAuthenticatedResult() {
        val s=ready(); s.signal(FaceSignal.ACCEPTED,100,100); s.signal(FaceSignal.STOPPED,120,120)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150);s.userPresent(200,true)
        s.tick(600);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun noRepeatedSuccessAndNoBusyTimerAfterExpiry() {
        val s=ready();s.signal(FaceSignal.ACCEPTED,100,100);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150)
        s.userPresent(200,true);s.tick(600);assertEquals(FacePhase.SUCCESS,s.phase)
        s.tick(1800); assertEquals(FacePhase.HIDDEN,s.phase); assertNull(s.deadline)
        s.signal(FaceSignal.ACCEPTED,1900,1900); s.userPresent(1950,true); s.tick(2400)
        assertEquals(FacePhase.HIDDEN,s.phase)
    }
    @Test fun screenOffOrSourceLossDropsAllPendingEvidence() {
        val s=ready();s.signal(FaceSignal.ACCEPTED,100,100);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150)
        s.clear();s.userPresent(200,true);s.tick(600)
        assertEquals(FacePhase.HIDDEN,s.phase); assertNull(s.deadline)
    }
    @Test fun aNewLockHasIndependentEvidence() {
        val s=ready(); s.signal(FaceSignal.OTHER_UNLOCK,100,100); s.lock(1000)
        s.signal(FaceSignal.ACCEPTED,1100,1100);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,1150,1150)
        s.userPresent(1200,true);s.tick(1600)
        assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun scanHasABoundedLifetime() {
        val s=ready(); s.signal(FaceSignal.START,10,10); s.tick(3210)
        assertEquals(FacePhase.HIDDEN,s.phase); assertNull(s.deadline)
    }
    @Test fun nonCredentialEntryAndPresenceNeverReplaceFaceAuthentication() {
        val s=ready();s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150);s.userPresent(200,true)
        assertEquals(FacePhase.HIDDEN,s.phase)
        s.tick(1600);assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
    }
    @Test fun faceAndNonCredentialEntryNeverReplaceVerifiedPresence() {
        val s=ready();s.signal(FaceSignal.ACCEPTED,100,100);s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,150,150)
        s.tick(1400);assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
    }
    @Test fun settlingStartsAfterTheLastPositiveProofIsReceived() {
        val s=ready();s.signal(FaceSignal.ACCEPTED,100,100);s.userPresent(200,true)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,180,700)
        assertEquals(FacePhase.HIDDEN,s.phase);assertEquals(1100L,s.deadline)
        s.tick(1099);assertEquals(FacePhase.HIDDEN,s.phase)
        s.tick(1100);assertEquals(FacePhase.SUCCESS,s.phase)
    }
    @Test fun allThreeEvidenceTimesMustFitTheSamePairingWindow() {
        val s=ready();s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,0,0)
        s.signal(FaceSignal.ACCEPTED,100,100);s.userPresent(1300,true);s.tick(1700)
        assertEquals(FacePhase.HIDDEN,s.phase);assertNull(s.deadline)
    }
    @Test fun previousEpochFutureAndOverdueEntryProofsAreIgnored() {
        for((at,now) in listOf(9L to 200L,500L to 200L,20L to 1800L)) {
            val s=FaceSession().apply{lock(10)}
            s.signal(FaceSignal.ACCEPTED,now-100,now-100);s.userPresent(now,true)
            s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,at,now);s.tick(now+500)
            assertEquals(FacePhase.HIDDEN,s.phase)
        }
    }
    @Test fun verifiedPresenceFromBeforeTheEpochCannotCompleteFreshPositiveProofs() {
        val s=FaceSession().apply{lock(100)}
        s.userPresent(90,true);s.signal(FaceSignal.ACCEPTED,110,110)
        s.signal(FaceSignal.NON_CREDENTIAL_ENTRY,120,120);s.tick(520)
        assertEquals(FacePhase.HIDDEN,s.phase)
    }
}
