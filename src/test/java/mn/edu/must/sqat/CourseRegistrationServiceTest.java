package mn.edu.must.sqat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CourseRegistrationService-ийн нэгжийн тестүүд.
 * StudentRepository, EmailSender хоёулаа Mockito-гийн mock — сан, SMTP сервер огт хэрэггүй.
 */
@ExtendWith(MockitoExtension.class)
class CourseRegistrationServiceTest {

    private static final String STUDENT_ID = "B222270080";
    private static final String EMAIL = "student@must.edu.mn";
    private static final String COURSE_ID = "CSA313";
    private static final String PREREQ = "CSA201";

    @Mock StudentRepository repository;
    @Mock EmailSender emailSender;

    CourseRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new CourseRegistrationService(repository, emailSender);
    }

    private static Student activeStudent(List<String> coursesTaken) {
        return new Student(STUDENT_ID, EMAIL, true, coursesTaken);
    }

    /** Бүх шалгалтыг давдаг үндсэн (happy path) тохиолдлын stub-ууд. */
    private void stubHappyPath() {
        when(repository.findStudent(STUDENT_ID)).thenReturn(activeStudent(List.of(PREREQ)));
        when(repository.courseExists(COURSE_ID)).thenReturn(true);
        when(repository.prerequisites(COURSE_ID)).thenReturn(List.of(PREREQ));
    }

    // A1 — when + verify
    @Test
    void register_activeStudentWithPrerequisites_returnsOkSavesAndSendsEmail() {
        stubHappyPath();

        String result = service.register(STUDENT_ID, COURSE_ID);

        assertEquals("OK", result);
        verify(repository).saveRegistration(STUDENT_ID, COURSE_ID);
        verify(emailSender).send(eq(EMAIL), anyString(), contains(COURSE_ID));
    }

    // A2 — verify(..., never())
    @Test
    void register_unknownStudent_returnsErrorNoStudentAndDoesNotSaveOrEmail() {
        when(repository.findStudent(STUDENT_ID)).thenReturn(null);

        String result = service.register(STUDENT_ID, COURSE_ID);

        assertEquals("ERROR_NO_STUDENT", result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    // A3 — verifyNoInteractions
    @Test
    void register_inactiveStudent_returnsErrorInactiveAndNeverTouchesEmail() {
        when(repository.findStudent(STUDENT_ID))
                .thenReturn(new Student(STUDENT_ID, EMAIL, false, List.of(PREREQ)));

        String result = service.register(STUDENT_ID, COURSE_ID);

        assertEquals("ERROR_INACTIVE_STUDENT", result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }

    // A4 — when + never
    @Test
    void register_missingPrerequisite_returnsErrorPrerequisitesAndDoesNotSave() {
        when(repository.findStudent(STUDENT_ID)).thenReturn(activeStudent(List.of(PREREQ)));
        when(repository.courseExists(COURSE_ID)).thenReturn(true);
        when(repository.prerequisites(COURSE_ID)).thenReturn(List.of(PREREQ, "CSA210"));

        String result = service.register(STUDENT_ID, COURSE_ID);

        assertEquals("ERROR_PREREQUISITES", result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    // A5 — doThrow(...).when(...)
    @Test
    void register_emailSenderThrows_stillReturnsOkAndRegistrationIsSaved() {
        stubHappyPath();
        doThrow(new RuntimeException("SMTP сервер унасан"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        String result = service.register(STUDENT_ID, COURSE_ID);

        assertEquals("OK", result);
        verify(repository).saveRegistration(STUDENT_ID, COURSE_ID);
    }

    // A6 — assertThrows. Хоёр аргумент тус бүрийн null-ийг шалгана: эхэндээ зөвхөн studentId-г
    // шалгадаг байсан тул "|| courseId == null"-г устгасан мутант 3-ыг барьж чадаагүй.
    @Test
    void register_nullStudentIdOrCourseId_throwsIllegalArgumentAndNoInteractions() {
        assertThrows(IllegalArgumentException.class, () -> service.register(null, COURSE_ID));
        assertThrows(IllegalArgumentException.class, () -> service.register(STUDENT_ID, null));

        verifyNoInteractions(repository, emailSender);
    }

    // A7 — ArgumentCaptor
    @Test
    void register_success_emailBodyContainsCourseId() {
        stubHappyPath();
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        service.register(STUDENT_ID, COURSE_ID);

        verify(emailSender).send(eq(EMAIL), anyString(), bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains(COURSE_ID),
                "И-мэйлийн биед хичээлийн код байх ёстой: " + bodyCaptor.getValue());
    }

    // A8 — @ParameterizedTest + @CsvSource
    // Зарим мөр stub-уудын зөвхөн нэг хэсгийг ашигладаг (жишээ нь NO_STUDENT мөр courseExists-д
    // хүрэхгүй), тиймээс strict stubs-ийн UnnecessaryStubbingException-ээс сэргийлж lenient() хэрэглэв.
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
            "NO_STUDENT,    ERROR_NO_STUDENT",
            "INACTIVE,      ERROR_INACTIVE_STUDENT",
            "MISSING_PREREQ, ERROR_PREREQUISITES"
    })
    void register_invalidCase_returnsErrorAndDoesNotSaveOrEmail(String scenario, String expected) {
        Student student = switch (scenario) {
            case "NO_STUDENT" -> null;
            case "INACTIVE" -> new Student(STUDENT_ID, EMAIL, false, List.of(PREREQ));
            case "MISSING_PREREQ" -> activeStudent(List.of());
            default -> throw new IllegalStateException("Тодорхойгүй хувилбар: " + scenario);
        };
        lenient().when(repository.findStudent(STUDENT_ID)).thenReturn(student);
        lenient().when(repository.courseExists(COURSE_ID)).thenReturn(true);
        lenient().when(repository.prerequisites(COURSE_ID)).thenReturn(List.of(PREREQ));

        String result = service.register(STUDENT_ID, COURSE_ID);

        assertEquals(expected, result);
        verify(repository, never()).saveRegistration(anyString(), anyString());
        verifyNoInteractions(emailSender);
    }
}
