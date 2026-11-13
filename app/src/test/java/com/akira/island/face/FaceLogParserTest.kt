package com.akira.island.face

import org.junit.Assert.*
import org.junit.Test

class FaceLogParserTest {
    @Test fun unknownSystemUiIdentityDisablesOnlyThatSourceWithoutWeakeningAuthentication() {
        val p=FaceLogParser(FaceLogSources(40,80,-1,60,0))
        assertEquals(FaceSignal.ACCEPTED,p.parse(record(auth())))
        assertNull(p.parse(ui("Succeeded(biometricSourceType=FACE, isStrongBiometric=true, requestReason=DeviceEntryAuthentication, userId=0)")))
        assertNull(p.parse(record(auth(bp=true))))
    }
    private val parser=FaceLogParser(FaceLogSources(40,80,10055,60,0))
    private fun auth(type:String="face", ok:Boolean=true, id:Int=7, owner:String="com.android.systemui", bp:Boolean=false,user:Int=0) =
        "onAuthenticated($ok), ID:$id, Owner: $owner, isBP: $bp, listener: synthetic, requireConfirmation: false, user: $user, clientMonitor: {[5] com.android.server.biometrics.sensors.$type.aidl.${if(type=="face") "Face" else "Fingerprint"}AuthenticationClient, proto=3, owner=$owner, cookie=0, requestId=11, userId=$user}"
    private fun record(message:String, uid:Int=1000,pid:Int=40,tag:String="Biometrics/AuthenticationClient")=SystemLogRecord(uid,pid,0,tag,message)
    private fun ui(body:String)=record("authenticationState updated: $body",10055,80,"BiometricStatusRepositoryImpl")
    @Test fun keyguardFaceIsRecognizedAndFingerprintIsAVeto() {
        assertEquals(FaceSignal.ACCEPTED,parser.parse(record(auth())))
        assertEquals(FaceSignal.OTHER_UNLOCK,parser.parse(record(auth(type="fingerprint"))))
    }
    @Test fun forgedTagsAndStaleProcessIdentitiesAreRejected() {
        assertNull(parser.parse(record(auth(),uid=10101)))
        assertNull(parser.parse(record(auth(),pid=41)))
        assertNull(parser.parse(record(auth(),tag="Fake/Biometrics/AuthenticationClient")))
        assertNull(parser.parse(record(auth(),uid=0)))
    }
    @Test fun failedOrZeroIdIsNeverASuccess() {
        assertEquals(FaceSignal.REJECTED,parser.parse(record(auth(ok=false))))
        assertNull(parser.parse(record(auth(id=0))))
        assertNull(parser.parse(record(auth(id=-1))))
    }
    @Test fun appPromptAndOtherUsersAreNotDeviceEntry() {
        assertNull(parser.parse(record(auth(bp=true))))
        assertNull(parser.parse(record(auth(owner="com.example.player"))))
        assertNull(parser.parse(record(auth(user=10))))
    }
    @Test fun finishingAClientOrDetectingAFaceIsNotAuthentication() {
        for(message in listOf("Finishing FaceAuthenticationClient success:true", "faceDetected", "Face authenticated", "Unlock success"))
            assertNull(parser.parse(record(message)))
    }
    @Test fun onlyExplicitDeviceEntrySystemUiStatesAreAccepted() {
        assertEquals(FaceSignal.START,parser.parse(ui("Started(biometricSourceType=FACE, requestReason=DeviceEntryAuthentication)")))
        assertEquals(FaceSignal.ACCEPTED,parser.parse(ui("Succeeded(biometricSourceType=FACE, isStrongBiometric=true, requestReason=DeviceEntryAuthentication, userId=0)")))
        assertNull(parser.parse(ui("Succeeded(biometricSourceType=FACE, isStrongBiometric=true, requestReason=BiometricPromptAuthentication, userId=0)")))
        assertNull(parser.parse(ui("Succeeded(biometricSourceType=FACE, isStrongBiometric=true, requestReason=DeviceEntryAuthentication, userId=10)")))
        assertNull(parser.parse(ui("Succeeded(biometricSourceType=FACE, requestReason=DeviceEntryAuthentication)")))
    }
    @Test fun duplicateFieldsAndForgedSystemUiMetadataAreIgnored() {
        assertNull(parser.parse(ui("Succeeded(biometricSourceType=FACE, requestReason=DeviceEntryAuthentication, userId=0, userId=10)")))
        assertNull(parser.parse(record("authenticationState updated: Started(biometricSourceType=FACE, requestReason=DeviceEntryAuthentication)",10056,80,"BiometricStatusRepositoryImpl")))
    }
    @Test fun trustedCredentialSourceIsAnExplicitVeto() {
        val message="authorization.rs: on_device_unlocked(user_id=0, password.is_some()=true)"
        assertEquals(FaceSignal.OTHER_UNLOCK,parser.parse(record(message,1017,60,"keystore2")))
        assertNull(parser.parse(record(message,10101,60,"keystore2")))
        assertEquals(FaceSignal.NON_CREDENTIAL_ENTRY,parser.parse(record(message.replace("=true","=false"),1017,60,"keystore2")))
        assertNull(parser.parse(record(message.replace("user_id=0","user_id=10"),1017,60,"keystore2")))
    }
    @Test fun nonCredentialEntryRequiresTheTrustedPinnedProcessCurrentUserAndExplicitFalse() {
        val message="authorization.rs: on_device_unlocked(user_id=0, password.is_some()=false)"
        assertEquals(FaceSignal.NON_CREDENTIAL_ENTRY,parser.parse(record(message,1017,60,"keystore2")))
        for(r in listOf(record(message,10101,60,"keystore2"),record(message,1017,61,"keystore2"),
            record(message.replace("user_id=0","user_id=10"),1017,60,"keystore2"),
            record(message.replace("=false","=unknown"),1017,60,"keystore2"),
            record(message.replace(", password.is_some()=false",""),1017,60,"keystore2"))) {
            assertNull(parser.parse(r))
        }
    }
    @Test fun unknownCredentialFormatsAndRestartedSourcesCannotAuthenticateAFaceEntry() {
        for(r in listOf(record("on_device_unlocked(user_id=0, new_format=true)",1017,60,"keystore2"),
            record("on_device_unlocked(user_id=0, password.is_some()=false)",1017,61,"keystore2"))) {
            val s=FaceSession().apply{lock(0)}
            parser.parse(record(auth()))?.let{s.signal(it,100,100)}
            parser.parse(r)?.let{s.signal(it,150,150)}
            s.userPresent(200,true);s.tick(600)
            assertEquals(FacePhase.HIDDEN,s.phase)
            s.tick(1400);assertNull(s.deadline)
        }
    }
}
