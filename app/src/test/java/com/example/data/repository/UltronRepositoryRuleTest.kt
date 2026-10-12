package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class UltronRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveTask_authenticatedOwner_succeeds(): Unit = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = UltronRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createTask(
                title = "Solve Organic Chemistry Chapter 4",
                subject = "Chemistry",
                deadline = "Tomorrow",
                priority = "HIGH",
                category = "TODAY"
            )
        }
        assertThat(createResult.isSuccess).isTrue()
        val taskId = createResult.getOrThrow()
        assertThat(taskId).isNotEmpty()

        val emittedTasks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTasks(uid).first { list -> list.any { it.id == taskId } }
        }
        assertThat(emittedTasks.map { it.id }).contains(taskId)
    }

    @Test
    fun getTaskById_crossUserAccess_failsWithPermissionDenied(): Unit = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = UltronRepository(firestore, auth)
        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createTask("Physics Kinematics", "Physics").getOrThrow()
        }

        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = UltronRepository(firestore, auth)
        val exception = assertFailsWith<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getTaskById(aliceUid, taskId).getOrThrow()
            }
        }
        assertThat(exception.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun observeTasks_unauthenticatedUser_failsWithPermissionDenied(): Unit = runBlocking {
        auth.signOut()
        val repository = UltronRepository(firestore, auth)

        val exception = assertFailsWith<FirebaseFirestoreException> {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTasks("unauthenticated_user_target").first()
            }
        }
        assertThat(exception.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun submitUpiPaymentRequest_createsPendingRequest_andStudentCannotSelfApprove(): Unit = runBlocking {
        val uid = signInTestUser("student_${UUID.randomUUID()}@test.com")
        val repository = UltronRepository(firestore, auth)

        val reqId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.submitUpiPaymentRequest(
                userId = uid,
                studentName = "Rohan Verma",
                studentEmail = "rohan@test.com",
                selectedPlan = "PRO",
                amount = 299,
                utr = "412398765432",
                notificationStatus = "SENT"
            ).getOrThrow()
        }
        assertThat(reqId).isNotEmpty()

        val userPayments = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserPaymentRequests(uid).first { list -> list.any { it.id == reqId } }
        }
        val payment = userPayments.first { it.id == reqId }
        assertThat(payment.status).isEqualTo("PENDING")

        // Attempting to self-approve as student must fail with PERMISSION_DENIED
        val exception = assertFailsWith<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.adminProcessPaymentRequest(
                    request = payment,
                    approve = true,
                    adminEmail = "rohan@test.com",
                    adminNote = "Trying to approve my own payment",
                    notificationStatus = "HACKED"
                ).getOrThrow()
            }
        }
        assertThat(exception.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun schoolPortal_rbacEnforced_onlyTeacherPrincipalAdminCanCreate_andStudentCanGroupChat(): Unit = runBlocking {
        // 1. Student cannot create a School Portal
        val studentEmail = "student_${UUID.randomUUID()}@test.com"
        val studentUid = signInTestUser(studentEmail)
        val studentRepo = UltronRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.getOrCreateProfile(studentUid, "Aarav Student", studentEmail).getOrThrow()
        }

        val studentCreateFail = assertFailsWith<IllegalArgumentException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                studentRepo.createSchoolPortal(
                    creatorId = studentUid,
                    creatorName = "Aarav Student",
                    creatorRole = "STUDENT",
                    schoolName = "Ultron High",
                    groupName = "Unauthorized Group",
                    description = "Should fail",
                    announcement = "Notice"
                ).getOrThrow()
            }
        }
        assertThat(studentCreateFail.message).contains("Only Admins, Teachers, or Principals")

        // 2. Teacher can create a School Portal, and Student can join & chat in the group
        val teacherEmail = "teacher_${UUID.randomUUID()}@test.com"
        val teacherUid = signInTestUser(teacherEmail)
        val teacherRepo = UltronRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            teacherRepo.getOrCreateProfile(teacherUid, "Prof. Sharma", teacherEmail).getOrThrow()
            teacherRepo.updateProfileDetails(
                userId = teacherUid,
                fullName = "Prof. Sharma",
                studentClass = "Class 12",
                section = "A",
                schoolName = "Ultron Academy",
                dob = "1985-06-15",
                age = 40,
                role = "TEACHER",
                language = "English",
                themePreference = "DARK"
            ).getOrThrow()
        }

        val portalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            teacherRepo.createSchoolPortal(
                creatorId = teacherUid,
                creatorName = "Prof. Sharma",
                creatorRole = "TEACHER",
                schoolName = "Ultron Academy",
                groupName = "Class 12 Physics & Calculus Portal",
                description = "Group collaboration for Teachers, Students & Principal",
                announcement = "Midterm Physics Exam on Friday"
            ).getOrThrow()
        }
        assertThat(portalId).isNotEmpty()

        // Switch back to Student and post a group chat message in the portal
        signInTestUser(studentEmail)
        val portals = withTimeout(FLOW_TIMEOUT_MS) {
            studentRepo.observeSchoolPortals().first { list -> list.any { it.id == portalId } }
        }
        val targetPortal = portals.first { it.id == portalId }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            studentRepo.postSchoolPortalChatOrToggleVoice(
                portal = targetPortal,
                senderName = "Aarav Student",
                senderRole = "STUDENT",
                message = "Sir, can you share the Rotational Motion formula sheet?"
            ).getOrThrow()
        }

        val updatedPortals = withTimeout(FLOW_TIMEOUT_MS) {
            studentRepo.observeSchoolPortals().first { list ->
                list.any { it.id == portalId && it.chatTranscript.contains("Rotational Motion") }
            }
        }
        val portalAfterStudentChat = updatedPortals.first { it.id == portalId }
        assertThat(portalAfterStudentChat.chatTranscript)
            .contains("Aarav Student (STUDENT): Sir, can you share the Rotational Motion formula sheet?")

        // Teacher updates homework and timetable in the portal
        signInTestUser(teacherEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            teacherRepo.postSchoolPortalChatOrToggleVoice(
                portal = portalAfterStudentChat,
                senderName = "Prof. Sharma",
                senderRole = "TEACHER",
                message = "Uploaded Rotational Motion Homework",
                updatedHomework = "Solve Chapter 7 Q1-Q12",
                updatedTimetable = "Physics Extra Class: Sat 10:00 AM"
            ).getOrThrow()
        }

        // Principal creates a PENDING_APPROVAL school portal request and then approves/activates it
        val principalEmail = "principal_${UUID.randomUUID()}@test.com"
        val principalUid = signInTestUser(principalEmail)
        val principalRepo = UltronRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            principalRepo.getOrCreateProfile(principalUid, "Dr. Nair Principal", principalEmail).getOrThrow()
            principalRepo.updateProfileDetails(
                userId = principalUid,
                fullName = "Dr. Nair Principal",
                studentClass = "All Classes",
                section = "All",
                schoolName = "St. Xavier National School",
                dob = "1978-03-10",
                age = 48,
                role = "PRINCIPAL",
                language = "English",
                themePreference = "DARK"
            ).getOrThrow()

            val pendingPortalId = principalRepo.createSchoolPortal(
                creatorId = principalUid,
                creatorName = "Dr. Nair Principal",
                creatorRole = "PRINCIPAL",
                schoolName = "St. Xavier National School",
                groupName = "Official School Portal",
                description = "Principal Portal Request",
                announcement = "Welcome to the new academic year",
                portalStatus = "PENDING_APPROVAL"
            ).getOrThrow()

            val pendingPortal = principalRepo.observeSchoolPortals()
                .first { list -> list.any { it.id == pendingPortalId } }
                .first { it.id == pendingPortalId }
            assertThat(pendingPortal.portalStatus).isEqualTo("PENDING_APPROVAL")

            principalRepo.adminReviewOrModeratePortal(
                portal = pendingPortal,
                newStatus = "APPROVED",
                adminEmail = "cannies.studio@gmail.com",
                moderationReason = "Verified School Portal & Academic Workspace"
            ).getOrThrow()
        }
        Unit
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 10000L
    }
}
