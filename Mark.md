# Project Report: Mark AI Assistant

Mark is a multi-device personal AI assistant designed to bridge the gap
between your phone, your Wear OS watch, and your Windows laptop. It uses
a hybrid intelligence model: a local Regex-based routing engine for
lightning-fast offline commands and the Groq (Llama 3) LLM for complex
natural language understanding.

## 1. System Architecture

The project is divided into three core modules:

### :core (Android Library)

The brain of Mark. It contains the intent router, the tool execution
manager, and all shared business logic.

### :app (Android Application)

The primary interface on the phone. It performs heavy operations such as
contact resolution, SMS and call execution, and also serves as the
communication gateway for the Wear OS application.

### :wear (Wear OS Application)

A lightweight voice-first interface that offloads complex operations to
the phone using the Wearable Data Layer while providing a seamless
smartwatch experience.

------------------------------------------------------------------------

## 2. Core Features & Capabilities

### A. Device & Environment Control

-   Control Flashlight, Do Not Disturb (DND), Silent Mode, and
    Auto-Rotate.
-   Adjust Screen Brightness and System Volume using absolute values
    (e.g., 50%) or relative commands (e.g., "increase the volume").
-   Retrieve battery level, storage information, and connectivity
    status.

### B. Productivity & Time Management

-   Built-in Task Manager for adding, viewing, and completing tasks.
-   Set, query, and cancel Alarms and Timers.
-   View upcoming Calendar events and agenda.
-   Stopwatch support.

### C. Health & Fitness

Integrated with Android Health Connect and Wear OS Health Services to
provide:

-   Daily step count
-   Real-time heart rate
-   Sleep tracking

### D. Communication

-   Smart contact resolution for phone calls.
-   Send SMS using natural language.
-   Read recent notifications or report unread notification count.

### E. Navigation & Information

-   Navigate to destinations.
-   Find nearby places.
-   Estimate travel distance.
-   Weather information through OpenWeatherMap.
-   Complex queries handled through Groq Llama 3 when offline routing
    confidence is low.

------------------------------------------------------------------------

## 3. Cross-Device Laptop Control

Mark can remotely control a Windows laptop over the local network using
a dedicated Python agent.

Capabilities include:

-   Lock, Sleep, Restart, and Shutdown
-   Launch and close desktop applications
-   Trigger Gradle builds and developer workflows
-   Browser search
-   Clipboard read/write
-   Screenshots
-   Screen recording
-   Folder shortcuts
-   Media control

------------------------------------------------------------------------

## 4. Intelligent Routing Engine (v2)

The routing pipeline consists of:

1.  **Normalization**
    -   Cleans speech recognition output.
    -   Handles English/Hinglish number conversion.
    -   Corrects common STT errors.
2.  **Prefilter Index**
    -   Narrows roughly 90 intent rules down to only the most relevant
        candidates before regex evaluation.
3.  **Token-Coverage Confidence**
    -   Scores how well the user's sentence matches the detected intent.
    -   Low-confidence requests are automatically forwarded to the Llama
        3 model.
4.  **Context Follow-ups**
    -   Understands follow-up commands such as "turn it off" by
        remembering the previously executed intent.

------------------------------------------------------------------------

## 5. Persona & Language

-   Native support for English and Hinglish voice commands.
-   Context-aware conversational responses.
-   Configurable assistant personality and language behavior.
