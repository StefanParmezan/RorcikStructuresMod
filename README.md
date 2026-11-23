<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&height=160&color=0:36BCF7,50:6C63FF,100:FF4F9A&text=Rorcik%20Structures&fontSize=44&fontColor=ffffff&fontAlignY=38&desc=Procedural%20city%20generation%20for%20Forge%201.20.1&descAlignY=60&descSize=16&animation=fadeIn" alt="Rorcik Structures" width="100%" />

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=for-the-badge)
![Forge](https://img.shields.io/badge/Forge-47.4.10-E04E14?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![JUnit 5](https://img.shields.io/badge/JUnit_5-25A162?style=for-the-badge&logo=junit5&logoColor=white)
![RorcikCore](https://img.shields.io/badge/RorcikCore-1.0.0-6C63FF?style=for-the-badge)
![License](https://img.shields.io/badge/License-All_Rights_Reserved-B22222?style=for-the-badge)

</div>

## О проекте

**Rorcik Structures** — мод процедурной генерации городов и домов для Minecraft 1.20.1 (Forge).
Разрабатывается для модпака **The Last Dawn** и использует **RorcikCore** как общий слой для зон, реестров и `SavedData`.

Генерация рассчитана на большие города: задачи выполняются постепенно, с бюджетом времени на тик, чтобы не блокировать сервер.

## Возможности

- **Детерминированная решётка городов.** Кандидаты располагаются на треугольной решётке с джиттером, ранжированием и проверкой минимальной дистанции между городами.
- **Планирование раскладки.** Дорожный граф, слоты под здания, парки и городские зоны; отдельные генераторы для `plains` и `desert`.
- **Процедурная генерация.** Дороги, перекрёстки, здания, дома с комнатами, тротуары и парки собираются из NBT-шаблонов и JSON-конфигов.
- **Tick-driven scheduler.** Приоритетная очередь задач, лимит времени на тик, chunk tickets, отложенные задачи и повторные попытки при неудаче.
- **Журнал и восстановление.** `CityJournal` сохраняет прогресс по слотам и дорогам, а незавершённые города восстанавливаются после перезагрузки сервера.
- **Команды.** `/locatecity` ищет ближайший город, `/planycity` резервирует город в текущей позиции.
- **Тесты.** Покрыты планировщик раскладки, решётка кандидатов, провайдер зон и задачи генерации.

## Как это работает

1. Мод строит детерминированную решётку кандидатов и выбирает валидные точки для городов.
2. При подходе игрока зона резервируется и для города строится план: дороги, слоты зданий, парки.
3. План превращается в очередь задач с приоритетами.
4. Scheduler выполняет задачи порциями по тикам, запрашивая chunk tickets для нужных чанков.
5. Прогресс пишется в журнал, поэтому генерацию можно продолжить после перезапуска.

## Стек

| Компонент | Версия |
|:---|:---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| Java | 17 |
| RorcikCore | 1.0.0 |
| Тесты | JUnit 5 |
| Команды | Brigadier |

## Зависимости

- **RorcikCore** — обязательная зависимость: зоны, реестры, `SavedData`.
- **Forge 1.20.1** — среда загрузки мода.

## Структура проекта

```text
src/main/java/ru/rorcik/structures/
├── api/                 # Интерфейсы загрузчиков и генераторов
├── city/                # Типы городов
├── command/             # /locatecity и /planycity
├── common/              # Конфиг, пути, точка входа
├── data/
│   ├── city/plains/
│   │   ├── config_loaders/  # Загрузчики JSON-конфигов
│   │   └── containers/      # Дома, дороги, слоты, сети
│   └── ...
├── generation/
│   ├── city/desert/         # Генерация пустынных городов
│   ├── city/plains/cities/  # Дороги, здания, парки, scheduler
│   ├── city/plains/houses/  # Дома и комнаты
│   └── ...
└── planning/            # Кандидаты, планировщик раскладки, зоны
```

Ресурсы мода:

```text
src/main/resources/data/rorcik_structures/
├── configs/cities/plains/   # JSON-конфиги зданий, дорог и парков
└── structures/cities/       # NBT-шаблоны городов, домов и комнат
```

## Сборка и запуск

Требования:

- JDK 17;
- Forge 1.20.1;
- Gradle Wrapper из репозитория;
- репозиторий `RorcikCore` рядом с проектом.

`RorcikStructuresMod` подключает ядро через composite build:

```groovy
includeBuild('../RorcikCore')
```

Ожидаемая структура папок:

```text
RorcikCore/
RorcikStructuresMod/
```

Команды:

```bash
./gradlew build
./gradlew test
./gradlew runClient
./gradlew runServer
./gradlew runData
```

## Команды

### `/locatecity [radius]`

Ищет ближайший город в радиусе (по умолчанию 10 000 блоков, максимум 30 000).

### `/planycity <plains|desert>`

Резервирует город указанного типа в текущей позиции. Требует уровень прав 2.

## Тесты

```bash
./gradlew test
```

Покрыты:

- `CityCandidateLocatorTest` — решётка кандидатов и минимальные дистанции;
- `CityLayoutPlannerTest` — планирование раскладки;
- `RandomCityZoneProviderTest` — провайдер случайных зон;
- `AnchorChunkTaskTest` — задачи генерации по якорным чанкам.

## Лицензия

Код мода распространяется на условиях **All Rights Reserved** (см. `gradle.properties`).
В `LICENSE.txt` находятся лицензионные уведомления Minecraft Forge и MCP, относящиеся к MDK.

## Авторы

- Rorcik
- [StefanParmezan](https://github.com/StefanParmezan)

Вопросы и предложения — Telegram: [@StefanParmezan](https://t.me/StefanParmezan).
