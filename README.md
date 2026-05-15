# VizoEye 👁️ AI Assistant for the Blind

**VizoEye** turns your smartphone camera into a personal AI guide. It instantly describes everything around you — from text in documents to faces of loved ones and landscape details — erasing the boundary between the visual world and sound.

*VizoEye — Your eyes in the world of AI.*

---  https://www.youtube.com/shorts/FeSkoNhPaK4

## ✨ Features

- **Three modes**:
  - *Brief*: Quick list of main objects.
  - *Detailed*: In-depth scene description, reading text, signs and documents.
  - *Voice Question*: AI dialogue about the photo. Supports context (remembers last 3 questions), allowing follow-up questions.
- **TTS (Text-to-Speech)**: Russian voice output with adjustable speed.
- **Flexibility**: Switch to direct Google Gemini usage (VPN required).

## 🌍 Works in Russia (No VPN)

Default setup uses **OpenRouter**.
- **NO VPN REQUIRED** for API operation.
- openrouter/auto selects from the top most powerful and stable models available on OpenRouter. The list changes based on server availability and AI updates, but typically cycles between:
  - Claude 3.5 Sonnet (Anthropic)
  - GPT-4o (OpenAI)
  - Llama 3.3 70B
- Stable response and high-quality image description.

---

## 🚀 Quick Start

Quick OpenRouter authorization via Google account and instant API key generation — just copy and paste.

1. **Get API Key (OpenRouter)**:
   - Go to [openrouter.ai](https://openrouter.ai/keys).
   - Click **Create Key**, enter any name and save the key.
2. **Configure App**:
   - Install VizoEye APK.
   - Tap **API Settings**.
   - Paste your key into **OpenRouter API Key** field and tap "Save".
   - Done! Now tap "Describe Surroundings".

---

## 🛠 Tech Stack

- **Kotlin & Jetpack Compose**: Modern UI and logic.
- **CameraX**: Android camera operations.
- **Clean Architecture**: Layer separation (data, domain, ui) for maintainable code.
- **Manual DI**: Lightweight `AppContainer` instead of heavy frameworks (Hilt/Koin).

## ⚡ Performance

Project focused on minimal latency.

## 🔒 Security

API keys protected using Android Keystore / EncryptedSharedPreferences.

## 📈 Roadmap

- [x] Voice control and AI dialogue.
- [ ] Smart glasses integration.
- [ ] Full offline mode (on-device recognition).

---

## 📜 License

MIT License. Copyright (c) 2026 VizoEye.

---

---

# VizoEye 👁️ AI-помощник для незрячих

**VizoEye** превращает камеру вашего смартфона в персонального ИИ-гида. Он мгновенно озвучивает всё, что происходит вокруг: от текста в документах до лиц близких и деталей ландшафта, стирая границы между визуальным миром и звуком.

*VizoEye — Твои глаза в мире ИИ.*

---  https://www.youtube.com/shorts/FeSkoNhPaK4

## ✨ Основные функции

- **Три режима**:
  - *Краткий*: Быстрое перечисление основных объектов.
  - *Подробный*: Детальное описание сцены, чтение текста, вывесок и документов.
  - *Голосовой вопрос*: Диалог с ИИ по фото. Поддерживает контекст (помнит последние 3 вопроса), что позволяет задавать уточняющие вопросы.
- **TTS (Text-to-Speech)**: Озвучивание результата на русском языке с возможностью регулировки скорости.
- **Гибкость**: Вы можете переключиться на прямое использование Google Gemini (если у вас есть VPN).

## 🌍 Работа в РФ (Без VPN)

По умолчанию приложение настроено на работу через **OpenRouter**.
- **VPN НЕ ТРЕБУЕТСЯ** для работы API.
- openrouter/auto выбирает модель из текущего топа самых мощных и стабильных систем, представленных на OpenRouter. Список постоянно меняется в зависимости от доступности серверов и обновлений ИИ, но обычно он циклично переключается между:
  - Claude 3.5 Sonnet (от Anthropic)
  - GPT-4o (от OpenAI)
  - Llama 3.3 70B
- Стабильный отклик и высокое качество описания изображений.

---

## 🚀 Как запустить

Быстрая авторизация на OpenRouter через аккаунт Google и мгновенное получение api key, остаётся только копировать и вставить.

1. **Получение API ключа (OpenRouter)**:
   - Зайдите на [openrouter.ai](https://openrouter.ai/keys).
   - Нажмите **Create Key**, введите любое имя и сохраните полученный ключ.
2. **Настройка приложения**:
   - Установите APK VizoEye.
   - Нажмите кнопку **Настройки API**.
   - Вставьте ваш ключ в поле **OpenRouter API Key** и нажмите "Сохранить".
   - Готово! Теперь можно нажимать "Описать окружение".

---

## 🛠 Технологический стек

- **Kotlin & Jetpack Compose**: Современный интерфейс и логика.
- **CameraX**: Работа с камерой Android.
- **Clean Architecture**: Разделение на слои (data, domain, ui) для поддерживаемости кода.
- **Manual DI**: Отказ от тяжелых фреймворков (Hilt/Koin) в пользу легковесного `AppContainer`.

## ⚡ Производительность

Проект ориентирован на минимальную задержку.

## 🔒 Безопасность

API-ключи защищены с использованием Android Keystore / EncryptedSharedPreferences.

## 📈 Планы развития

- [x] Голосовое управление и диалог с ИИ.
- [ ] Интеграция с умными очками.
- [ ] Полноценный оффлайн-режим (распознавание на устройстве).

---

## 📜 Лицензия

MIT License. Copyright (c) 2026 VizoEye.
