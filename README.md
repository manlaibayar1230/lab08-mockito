# Лаборатори №8: Mock объектууд — Mockito

F.CSA313 — Программ хангамжийн чанарын баталгаа ба тест (2026)

- **Оюутан:** Ө.Манлайбаяр
- **Код:** B222270080

## Орчин

```
$ java -version
openjdk version "17.0.21.0.2" 2026-10-22 LTS
OpenJDK Runtime Environment Microsoft-15779939 (build 17.0.21.0.2+1-LTS)
OpenJDK 64-Bit Server VM Microsoft-15779939 (build 17.0.21.0.2+1-LTS, mixed mode, sharing)

$ mvn -version
Apache Maven 3.9.9 (8e8579a9e76f7d015ee5ec7bfcdc97d260186937)
Maven home: C:\tools\apache-maven-3.9.9
Java version: 17.0.21.0.2, vendor: Microsoft, runtime: C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot
Default locale: en_US, platform encoding: Cp1252
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

## Бүтэц

```
lab08-mockito/
├── pom.xml
├── src/main/java/mn/edu/must/sqat/        # starter (өөрчлөөгүй)
│   ├── Student.java
│   ├── StudentRepository.java
│   ├── EmailSender.java
│   └── CourseRegistrationService.java
├── src/test/java/mn/edu/must/sqat/
│   └── CourseRegistrationServiceTest.java # A1–A8
└── results/
    ├── mvn-test.txt                       # эцсийн ногоон гаралт
    ├── mvn-test-mutant-1.txt
    ├── mvn-test-mutant-2.txt
    ├── mvn-test-mutant-3.txt              # A6-г зассаны дараа (мутант баригдсан)
    └── mvn-test-mutant-3-before-fix.txt   # A6-г засахаас өмнө (мутант амьд үлдсэн)
```

## Хэсэг A: Тестүүд

**Тестийн тоо: 10** (`results/mvn-test.txt`: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`) — 7 энгийн тест + A8-ийн 3 мөр.

| № | Тестийн метод | Хэрэгсэл |
|---|---|---|
| A1 | `register_activeStudentWithPrerequisites_returnsOkSavesAndSendsEmail` | `when` + `verify` |
| A2 | `register_unknownStudent_returnsErrorNoStudentAndDoesNotSaveOrEmail` | `verify(..., never())` |
| A3 | `register_inactiveStudent_returnsErrorInactiveAndNeverTouchesEmail` | `verifyNoInteractions` |
| A4 | `register_missingPrerequisite_returnsErrorPrerequisitesAndDoesNotSave` | `when` + `never` |
| A5 | `register_emailSenderThrows_stillReturnsOkAndRegistrationIsSaved` | `doThrow(...).when(...)` |
| A6 | `register_nullStudentIdOrCourseId_throwsIllegalArgumentAndNoInteractions` | `assertThrows` |
| A7 | `register_success_emailBodyContainsCourseId` | `ArgumentCaptor` |
| A8 | `register_invalidCase_returnsErrorAndDoesNotSaveOrEmail` (3 мөр) | `@ParameterizedTest` + `@CsvSource` |

Matcher-ийн дүрэм: нэг дуудалтад matcher хэрэглэсэн бол бүх аргументад хэрэглэсэн, жишээ нь `verify(emailSender).send(eq(EMAIL), anyString(), contains(COURSE_ID))`.

### A8 ба `lenient()`

A8-ийн мөр бүр `findStudent`, `courseExists`, `prerequisites` гурвыг ижилхэн stub хийдэг ч `NO_STUDENT`, `INACTIVE` мөрүүд код `courseExists`/`prerequisites`-д хүрэхээс өмнө буцдаг тул тэдгээр stub ашиглагдахгүй. `MockitoExtension`-ий strict stubs горим үүнийг `UnnecessaryStubbingException` гэж унагадаг (`lenient()`-гүйгээр ажиллуулахад `Errors: 2` гарсан), тиймээс эдгээр stub-ыг `lenient().when(...)`-ээр зориуд "ашиглагдахгүй байж болно" гэж тэмдэглэв.

## Хэсэг B: Мини мутаци

| Мутант | Өөрчлөлт | Үр дүн | Унасан тестүүд |
|---|---|---|---|
| 1 | `if (!student.active()) ...` комментлосон | `Failures: 2, Errors: 0` — BUILD FAILURE | A3 (`register_inactiveStudent_...`: хүлээгдсэн `ERROR_INACTIVE_STUDENT`, гарсан `ERROR_NO_COURSE`), A8 мөр [2] `INACTIVE` (гарсан `OK`) |
| 2 | `repository.saveRegistration(...)` комментлосон | `Failures: 2, Errors: 0` — BUILD FAILURE | A1 (`register_activeStudentWithPrerequisites_...`), A5 (`register_emailSenderThrows_...`) — `verify(repository).saveRegistration(...)` "Wanted but not invoked" |
| 3 | `\|\| courseId == null` устгасан | **Засахаас өмнө:** `Failures: 0` — BUILD SUCCESS (мутант амьд үлдсэн, `mvn-test-mutant-3-before-fix.txt`) <br> **Зассаны дараа:** `Failures: 1, Errors: 0` — BUILD FAILURE | A6 (`register_nullStudentIdOrCourseId_...`: "Expected IllegalArgumentException to be thrown, but nothing was thrown") |

Энэ гурван мутантад `UnnecessaryStubbingException` (Errors) гараагүй, бүгд Failures-аар унасан.

**Мутант 3-ын засвар:** A6 эхэндээ зөвхөн `register(null, COURSE_ID)`-г шалгадаг байсан тул `courseId`-ийн null шалгалтыг устгасан мутантыг огт мэдэрсэнгүй. A6-д `assertThrows(IllegalArgumentException.class, () -> service.register(STUDENT_ID, null))` нэмж, хоёр аргумент тус бүрийн null-ийг шалгадаг болгосны дараа мутант баригдсан. Тестийн тоо 10 хэвээр.

**Дүгнэлт:** Мутант 2-т A7 (ArgumentCaptor) унаагүй нь зөв — тэр тест зөвхөн и-мэйлийн агуулгыг шалгадаг, хадгалалтыг A1, A5 хариуцдаг. Мутант бүрийг дор хаяж нэг тест барьж байгаа ч мутант 3 эхэндээ амьд үлдсэн нь "тест ногоон байна" гэдэг нь "тест бүрэн" гэсэн үг биш гэдгийг харуулав: хил хязгаарын нөхцөл бүрийг (энд хоёр null) тусад нь шалгах ёстой.

## Дүгнэлт

Mock нь Mockito ажиллах үед автоматаар үүсгэдэг, ямар дуудлагад юу буцаахыг тест бүр өөрөө тодорхойлдог, мөн ямар метод хэдэн удаа, ямар аргументтай дуудагдсаныг шалгах (verify) боломжтой объект юм. Гараар бичсэн fake нь (жишээ нь `HashMap` дээр суурилсан in-memory repository) бодит ажиллагаатай хялбаршуулсан хэрэгжүүлэлт бөгөөд олон тестэд дахин ашиглагдаж, төлөв (state) хадгалдаг ч харилцан үйлдлийг өөрөө шалгадаггүй. Mock нь гадаад, удаан эсвэл тогтворгүй хамаарлыг (өгөгдлийн сан, SMTP, HTTP API) орлуулахад тохиромжтой; харин `Student` record, `List` зэрэг энгийн утгын объект, стандарт сангийн класс, өөрийн эзэмшдэггүй гуравдагч талын төрлийг шууд mock хийх ёсгүй — бодит объектыг нь хэрэглэх нь энгийн бөгөөд найдвартай. Мөн тестлэгдэж буй класс өөрийгөө (энд `CourseRegistrationService`) хэзээ ч mock хийж болохгүй, эс тэгвээс бодит логикийг огт шалгахгүй болно. `verify`-г хэтрүүлбэл тест нь "юу хийсэн" (үр дүн) биш "яаж хийсэн" (хэрэгжүүлэлтийн дэлгэрэнгүй) дээр тулгуурлаж, код рефакторинг хийхэд зан төлөв өөрчлөгдөөгүй ч тест унадаг хэврэг болдог. Тиймээс энэ лабд зөвхөн бизнесийн утгатай гаралтын үйлдлүүдийг (хадгалалт хийгдсэн эсэх, и-мэйл явсан эсэх) verify хийж, `findStudent`, `courseExists` зэрэг уншилтын дуудлагуудыг verify хийсэнгүй — тэдгээрийн үр нөлөө буцах утгаар аль хэдийн шалгагддаг. Хэсэг B харуулсанчлан mock-той тестийг кодыг зориуд эвдэж шалгах нь stub, verify-ийн тэнцвэр зөв эсэхийг батлах хамгийн энгийн арга юм.
