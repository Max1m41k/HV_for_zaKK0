# Порт на Minecraft 1.21.11 — статус и что проверять

Копия мода под 1.21.11. Эталон 1.21.4 живёт отдельно (D:\visualmod), общего compat-слоя нет:
праки в одном дереве не переносятся на другую версию автоматически.

Сборка: `./gradlew assemble "-Pmc=1.21.11"` → `visualsmod.jar` (jar собирает пользователь).

## Gates (проверено статически)

- Офлайн-javac по всему дереву порта: **0 ошибок** (433 файла, JDK 21, `--release 21`).
- Аудит mixin-таргетов по merged-джару 1.21.11: **148 хуков** (`Inject` 124, `ModifyVariable` 11,
  `Redirect` 8, `ModifyArg` 3, `ModifyConstant` 2), единственная запись — `NOJAVAP
  org.joml.Matrix3x2fStack` (ложная: joml не входит в merged-джар; `pushMatrix()`/`popMatrix()`
  подтверждены `javap` по joml 1.10.8). Каждый `method=`/`targets=` дескриптор, `@At`-target,
  синтаксис имени в `@At`-target (`AT-SYNTAX`), `@Shadow` и арность `@Inject` резолвятся.
  С 2026-10-31 аудит сверяет и **тип Callback** с возвращаемым типом цели: `CB-RET` (цель нет-void,
  а хэндлер берёт `CallbackInfo`) и `CB-VOID` (цель void, а хэндлер берёт `CallbackInfoReturnable`).
- Classpath для javac: `C:\hvchk\cp11.txt`, собирается `C:\hvchk\mkcp11.py` из
  `fabric-loom/1.21.11/mojang_minecraft_info.json` + `MC_PROFILES` в `build.gradle`;
  123 джара, ровно одна authlib (7.0.61), ни одного файла от 1.21.4.
- Отчёты: `C:\hvchk\audit.txt`, `C:\hvchk\javac_p25.txt`.

**Почему прошлый гейт врал.** Прежний `cp_w.txt` ссылался на `C:\hvchk\cp\depNN.jar`, где лежали
библиотеки обеих версий сразу, и javac брал первую совпавшую. authlib 6.0.57 перекрывала 7.0.61,
поэтому `GameProfile.getName()` считался валидным, гейт показывал 0 ошибок, а gradle (`authlib 7.0.61`)
падал с 13 ошибками. Новый classpath воспроизводит ошибки gradle один-в-один — значит, теперь
офлайн-проверка говорит то же, что скажет сборка.

**Краш старта из-за арности `@Inject` (лог пользователя, 19:42).** `ChatPlusMixin` нашёл таргет
`render(DrawContext, TextRenderer, int, int, int, boolean, boolean)`, но хэндлер был объявлен как
`(DrawContext, CallbackInfo)`. Mixin сравнивает полный дескриптор и падает:
`Invalid descriptor ... Expected (...7 аргументов..., CallbackInfo) but found (class_332, CallbackInfo)`
→ `MixinTransformerError` при загрузке `ChatHud`. Правило: хэндлер `@Inject` — либо **только
`CallbackInfo`**, либо **все аргументы цели подряд + `CallbackInfo`**; урезать середину нельзя, и
`require = 0` здесь не спасает (таргет-то найден). Старый аудит принимал любой префикс и потому
пропустил это; новый `check_inject` в `audit2.py` требует 0 или ровно N аргументов и сразу нашёл
ещё три таких же файла: `ChinaHatMixin`, `CosmeticsAttachMixin` (не хватало `CameraRenderState`),
`ItemPhysicsRenderMixin` (из 5 аргументов `renderStack` был только `MatrixStack`). Все четыре
исправлены — без этого игра падала бы последовательно на каждом.

**Вторая волна того же краша (лог пользователя, 19:52).** После чата игра упала на
`FirstPersonArmMixin.visuals$glassArm`: цели `renderArm(...ModelPart; Z)V` не хватало
`boolean leftSleeve`. Причина — второй слепой угол аудита: `method = RENDER_ARM` (и `TARGET` в
`ScoreboardEditMixin`, `M` в `ScoreboardTextMixin`) — ссылка на `static final String`, а `audit2.py`
искал только литералы, то есть эти хуки вообще не проверялись. Теперь константы разыменовываются;
регресс-тест (временно убран `leftSleeve`) даёт `ARITY FirstPersonArmMixin:35 ... handler args 5,
@Inject требует 0 или 6`, после возврата — 0 проблем.

**Встроенные ресурспаки (ошибки в том же логе, не краш).** Все пять паков
(`HolyVisualsBlade/Faithful/Glass/Shield/Totem`) не грузились: `Pack declares support for version
newer than 64, but is missing mandatory fields min_format and max_format`. Генератор
`RuntimePacks.writeMcmeta` писал старую схему `pack_format` + `supported_formats{1..9999}`.
В 1.21.11 `PackVersion.createRangeCodec` отвергает `supported_formats.max_inclusive > 64`, если нет
пары `min_format`/`max_format`; при наличии `min_format` поле `pack_format` обязано попадать в
[min, max] (иначе `Pack declared support for versions 75 to 9999 but declared main format is 1`).
Теперь пишется `{"description":..., "min_format":[75,0], "max_format":9999}` — `75.0` это
`resource_major`/`resource_minor` из `version.json` 1.21.11, берутся через
`SharedConstants.getGameVersion().packVersion(CLIENT_RESOURCES)`. Проверено офлайн-пробой
`C:\hvchk\PackMetaProbe.java` на реальном кодеке: новая схема → `[75.0, 9999.*]`, старая →
дословно та же ошибка из лога, `pack_format:1` рядом с `min_format` → отказ. Уже лежащие на диске
паки прошлых запусков правит разовый `RuntimePacks.upgradeLegacy()` из `ResourcePackManagerMixin`
(только папки `HolyVisuals*`, до сканирования каталога): по совпадению подписи паки не
пересобираются, поэтому без миграции ошибки висели бы и после обновления.

**Третья волна краша: синтаксис `@At`-таргета (лог пользователя, 20:38).** Игра упала на
`NoFogMixin`: `InvalidInjectionPointException ... Failed validating @At("INVOKE").target`, в корне —
`InvalidMemberDescriptorException: Invalid name: applyFog:`. Цель была записана в ASM-стиле
`Lnet/minecraft/client/render/fog/FogRenderer;applyFog:(Ljava/nio/ByteBuffer;...)V`, а Mixin в форме
`Lowner;name(desc)ret` берёт **всё до `(`** как имя — двоеточие попало в имя (`applyFog:`) и
`MemberInfo.validate` (regex `^<?[\w\p{Sc}]+>?$`) отверг его до всякой проверки таргета. Доказано
пробой `C:\hvchk\mix\ParseProbe.java` на самом Mixin 0.8.7: обе формы с `:` падают, форма без `:` —
`parsed OK`. Тот же дефект был ещё в трёх файлах (`AnimeFocusFogMixin`, `ShadersAtmosphereMixin`,
`SoftFadeFogMixin`) — все четыре исправлены; сама цель при этом верна: `javap -c` показывает вызов
`applyFog(ByteBuffer,int,Vector4f,float×6)` внутри `applyFog(Camera,...)` на offset 214, а нужный
local (`FogData`) жив на этом месте. Слепой угол аудита: regex цели в `audit2.py` требовал `(` сразу
после имени и такие строки молча пропускал. Теперь есть проверка `AT-SYNTAX` (имя цели — чистый
идентификатор, без `:`), а резолв ищет `:?` после имени, чтобы цель всё равно проверялась.

**Четвёртая волна: краш в мире (лог пользователя, 21:28).** Игра стартовала, но упала при заходе в мир:
`IllegalStateException: Missing elements in vertex: LineWidth` — сначала из `CombatPulse.drawPulse`
(перехвачено `Diag.once`), затем фатально из `CustomParticleManager.render`. Причина одна: в 1.21.11
у `RenderLayers.lines()` формат `POSITION_COLOR_NORMAL_LINE_WIDTH`, и `VertexConsumer` получил метод
`lineWidth(float)`; ванильный `VertexRendering.method_62299` пишет
`.vertex(...).color(int).normal(entry, Vector3f).lineWidth(float)` на каждую вершину. Наш
`WorldRenderUtil.line()` писал pos+color+normal, и `BufferBuilder` (`class_287.method_60806`) отвергал
 flush. Через `WorldRenderUtil` идут все 14 мест с `lines()` (CombatPulse, Prediction, HolyItems,
RespawnFx), прямых `.vertex(` в них нет — правка в одном методе закрывает всё. Второй стек
(`CustomParticleManager`) — не отдельный баг: исключение, поймавшееся внутри
`VertexConsumerProvider.Immediate`, оставляет буфер недописанным, и следующий `getBuffer(...)`
падает уже необратимо.

**Тот же лог: модели мечей не грузились (`Expected minU to be a Float, was null`).** В
`models/item/katana.json` (и `knight`, `scythe`) часть `"uv"` была `[null, v, u, null]`.
В 1.21.11 `ModelElementFace$Deserializer.getUV` читает ровно 4 обязательных float через
`JsonHelper.asFloat`, `null` → `JsonSyntaxException`; в 1.21.4 тот же `JsonHelper.asFloat` ведёт себя
так же, то есть модели были сломаны и там (пользователь играет стилем DAGGER и их не видел).
Старый генератор понимал «null» как «авто-ячейка»; фактический смысл — `[u2-4, v1, u2, v1+4]`
(атлас 4×4 по 16 px, в UV-единицах ячейка = 4). Переписано разовым `C:\hvchk\fix_uv.py`
(katana 108 фейсов, knight 84, scythe 132; `null` не осталось), та же правка внесена в
`D:\visualmod` — UV-формат версионно-нейтрален, код 1.21.4 не тронут.

## Что поменялось в API и как это закрыто

| 1.21.4 | 1.21.11 | где |
|---|---|---|
| `render(state, MatrixStack, VertexConsumerProvider, int)` | `render(state, MatrixStack, OrderedRenderCommandQueue, CameraRenderState)` | `ChinaHatMixin`, `CosmeticsAttachMixin`, `LivingEntityRendererMixin` |
| `renderArm(MatrixStack, VertexConsumerProvider, int, Identifier, ModelPart, boolean)` | `renderArm(MatrixStack, OrderedRenderCommandQueue, int, Identifier, ModelPart, boolean)` | `FirstPersonArmMixin` |
| `GameRenderer.renderHand(Camera, float, Matrix4f)` | `renderHand(float tickDelta, boolean advance, Matrix4f)` | `GameRendererMixin` |
| `Camera.update(BlockView, ...)` | `Camera.update(World, ...)` | `VictoryCameraMixin` |
| `Mouse.mouseClicked(double,double,int)` / `keyPressed(int,int,int)` | `mouseClicked(Click,boolean)` / `keyPressed(KeyInput)` (records) | `ItemScrollerMixin`, `ChatHudEditMixin`, `KeyboardKeyMixin`, `InventoryScreenMixin`, `InventorySearchCharMixin` |
| `ChatHud.mouseClicked` + `toChatLineX` | удалены; отступ чата — `translate(gutter, 0f)` после единственного `pushMatrix()` в `render(DrawContext, TextRenderer, IIIZZ)` (матрица снимается ванильным `popMatrix()`) | `ChatPlusMixin` |
| `GameProfile.getId/getName/getProperties()` | record-аксессоры `id()/name()/properties()` (authlib 7.0.61) | `PlayerBadgeMixin`, `PlayerListBadgeMixin`, `KillFrame`, `FakePlayer`, `Friends`, `NameProtectUtil`, `NametagUtil`, `TabSelf` |
| `pack.mcmeta`: `pack_format` + `supported_formats{1..9999}` | `min_format:[75,0]` + `max_format:9999`, без `pack_format` | `RuntimePacks.writeMcmeta` |
| `lines()` = `POSITION_COLOR_NORMAL` | `POSITION_COLOR_NORMAL_LINE_WIDTH`, у вершины обязателен `.lineWidth(float)` | `WorldRenderUtil.line` |
| `"uv": [null, v, u, null]` (сломанный формат) | только 4 числа: `[u2-4, v1, u2, v1+4]` — ячейка атласа | `models/item/{katana,knight,scythe}.json` |

Рисование в обход очереди идёт через `queue.submitCustom(MatrixStack, RenderLayer, (Entry, VertexConsumer) -> ...)`.
Очередь копирует матрицу в момент `submit`, поэтому отложенная команда рисует в том же базисе,
что и сиюминутная — поза/приседание/поворот тела не теряются.

Hotspot чата не сломан: `ChatHud$Interactable.calculateUntransformedOffset()` берёт
`DrawContext.getMatrices()`, инвертирует её и переводит курсор в координаты строки, то есть
наш `translate(gutter, 0f)` ванильный hover/учёт клика учитывают сами.

## Что подтвердилось, но требует проверки в игре

1. **Нижний (мировой) слой косметики** по-прежнему рисует в `mc.getBufferBuilders().getEntityVertexConsumers()`
   и вызывает `Immediate.draw(LAYER)`. Методы живы и компилируются, но в 1.21.11 сущности уходят
   в очередь команд — совпадёт ли порядок композитинга с 1.21.4, видно только в рантайме.
2. **Слои `debugQuads` / `debugFilledBox`** под новой очередью: у ChinaHat и прикрепляемой косметики
   нет ли дыр/артефактов глубины (в 1.21.4 это подбиралось polygon-offset).
3. **Стеклянная рука**: подмена потребителя внутри `submitCustom` вместо обёртки провайдера.
   `GlassItem.wrap(VertexConsumerProvider)` теперь используется только для предметов
   (`ItemGlassRenderMixin`); для руки — новый механизм.
4. **@ModifyConstant лимита чата (100)**: литерал проверен по байткоду — `bipush 100` есть и в
   `addMessage(ChatHudLine)`, и в `addVisibleMessage(ChatHudLine)`; в игре остаётся убедиться,
   что история растёт дальше 100 строк.
5. **Встроенные ресурспаки**: после правки формата в логе не должно быть
   `Couldn't load file/HolyVisuals... pack metadata`; сами паки должны появиться в списке
   ресурспаков и красить мечи/щиты/тотемы/стекло/текстуры.
6. **Линейные эффекты мира** (CombatPulse, Prediction, HolyItems, RespawnFx): после `.lineWidth`
   в логе не должно быть `Missing elements in vertex: LineWidth`; контуры и кольца видимы,
   толщина 1 px.
7. **Модели katana/knight/scythe**: в логе не должно быть `Failed to load model visualmod:models/item/...`;
   UV должны попадать в свои ячейки атласа (клинок/кромка/гарда/рукоять/древко/самоцвет), без
   растянутых или чужих фрагментов.

## Известные отличия, которые правятся только в рантайме

- `require = 0` на всех спорных хуках: если таргет всё же не соберётся, мода не крашит,
  а функция молча не работает. Симптом-чек: `Diag.once(...)` в этих местах.

## Волна 2 (2026-10-28): ивенты, табличка, перчатки, вход в мир

Перенесено из 1.21.4 без изменения логики, различия только в API версии:

| Что | 1.21.4 | 1.21.11 |
|---|---|---|
| Экран ивентов, анимация | `ctx.getMatrices().push()/translate(x,y,z)/scale(x,y,z)/pop()` | `pushMatrix()/popMatrix()`, `translate(x,y)` и `scale(x,y)` (Matrix3x2fStack, без z) |
| Клик по карточке | `mouseClicked(double,double,int)` | `mouseClicked(Click click, boolean doubled)`, координаты через `click.x()/y()/button()` |
| Звук нового ивента | `PositionedSoundInstance.master(...)` | `PositionedSoundInstance.ui(...)` |
| Масштаб кости косметики | `part.rotate(s)` + ручной `s.scale(xScale,yScale,zScale)` | не нужно: `part.applyTransform(s)` сам применяет origin/rotation/scale |

`WorldFade` (проявление картинки из черноты ~1 с после захода в мир) и обновлённый
`EventNotification` компилируются офлайн-гейтом (`javac -proc:none` по cp11.txt/srcs11.txt),
аудит миксинов — без новых замечаний.

## Волна 3 (2026-10-31): SlotsSwitch, копьё, вьюмодель на каждый меч, удаление VersionImitation

**SlotsSwitch (UTIL).** Два новых ряда «Key 1»/«Key 2» ловят нажатие так же, как ряд бинда
(`KeySetting` + захват в `ClickGUI`, ESC — отмена, Del/Backspace — очистить). При включении
модуля все бинды, сидящие на этих клавишах, обменены местами прямо в `mc.options.allKeys`
(затрагивает и хотбар, и debug-клавиши — `allKeys` собирается из `mainKeys` + `hotbarKeys` +
`debugKeys`), при выключении возвращаются байт в байт по журналу
`visualmod_configs/slots_switch.json`. Синхронизация идёт не только через `onEnable/onDisable`,
но и по `END_CLIENT_TICK`: `setEnabled()` из конфига колбэки не зовёт, а смена клавиш на лету
вообще ничем не сигнализируется. Если игра закрылась с обменом (краш, Alt+F4), журнал чинит
настройки управления на первом тике даже при выключенном модуле.

**Вьюмодель.** `ViewModelStore` переведён со `Kind` на строковые ключи: базовые (`sword`,
`spear`, …) плюс стилевые `sword_<стиль CustomSword>`. Стилевой профиль рождается копией
**текущего** общего профиля меча, поэтому при первом движении слайдера меч не подпрыгивает
к ванильным значениям, а общий профиль продолжает влиять на стили, у которых своих значений
ещё нет. Чип «Общий / Katana / …» под вкладкой меча, зелёная точка — у стиля есть своё;
вкладка копья появляется как обычный `Kind.SPEAR`.

**Что проверять в игре (новые пункты).**
8. **SlotsSwitch**: выбрать E и 2, включить — в «Настройках управления» строки «Инвентарь»
   и «Слот хотбара 2» поменялись биндами: E теперь выбирает слот 2, а «2» открывает инвентарь.
   Выключить — всё вернулось, журнал `slots_switch.json` удалён. Тот же тест с «1 → ЛКМ».
9. **Копьё**: на сервере HW (пак с `*_spear`) вкладка SPEAR двигает копьё; на трезубце из
   ваниля — нет (матч идёт по `ITEM_MODEL`, путь должен содержать «spear»). Копьё при этом
   не считается клинком: ауры/морф/жар на нём не рисуются (`matchBlade`).
10. **Пер-стиль**: настроить общий меч, выбрать чип DAGGER, сдвинуть Scale — катана не
    дёрнулась; RESET на чипе вернул даггер к общему профилю; после перезапуска значения
    обоих профилей остались раздельными (`viewmodel.json` содержит ключи `sword` и `sword_dagger`).

**VersionImitation удалён** (просьба пользователя): `VersionImitation`, `VersionAdvice`,
`ViaBridge`, `VersionImitationScreen`, `HandshakeMixin`, `VersionAdviceMixin` — файлов и строк
в `visualmod.mixins.json` больше нет, в окне «Сетевая игра» осталась одна кнопка «Записи».
`VersionTexturePacks` переименован в `PackFolder`: от него остались только `dir()`/`ensureDir()`
для каталога `<gamedir>/textures`, который регистрирует `ResourcePackManagerMixin`.

Гейт после волны: javac — 0 ошибок (433 файла), аудит — 148 хуков, 1 ложная запись `NOJAVAP`.

## Волна 4 (2026-10-31): feature control, SlotsSwitch-пары, косметика-кнопка, TNT, InterfaceHUD

**Feature Control (`api/LiteApi.java`) — все 8 пунктов ревью.** Отправка — сырые UTF-8 байты
JSON: `Data.write` больше не использует `writeByteArray()` (VarInt-длина ломала разбор на сервере),
`Data.read` берёт остаток пакета, а не `readByteArray`. Клиент fail-closed: с входа на внешний
сервер `applyLiteBlocklist(null)` скрывает всё, что можно скрыть, и таймаут 5 с блокировку не
снимает. После таймаута идёт повтор с бэкоффом 11 с → 60 с; лимит частоты переживает disconnect
(`nextSendAt` не сбрасывается). Ответ принимается только если есть `id` и `method` (`handle:199`),
`id` совпадает с нашим неотвеченным запросом, `ok == true` и `payload.blocklist` — массив строк
без пустых элементов (`readBlocklist:252`; иначе ответ не применяется, список не обнуляется).
Push-обновление: сообщение с `payload.blocklist`, но не `checkFeatures`, применяется сразу
(`handle:237`). Если сервер за 3 попытки не ответил и канал `liteapi:feature-control` не объявлен,
считаем, что протоколом он не владеет — контроль не применяется (`noControl`). При блокировке
модуля его панель настроек закрывается: `ClickGUI:237` сбрасывает `settingsModule`, если модуль
стал скрытым.

**SlotsSwitch — пары «источник → назначение».** До 4 пар, каждая — два ряда `KeySetting`
(«Откуда N»/«Куда N»), захват клавиши как у бинда. `applyPairs:118` меняет местами все бинды,
сидящие на этих клавишах (хотбар и debug тоже, `allKeys`), откат — `revert:167` по журналу
`visualmod_configs/slots_switch.json`; журнал чинит настройки управления и после краша.
Клавиши пустые или одинаковые — пара пропускается. Сами слоты не трогаем: меняется только
привязка клавиш.

**Косметика: одна кнопка.** Модулей-«плащ/шапка/броня» по размеру модуля в GUI больше нет: для
категории COSMETICS рисуется кнопка «Косметика» (`ClickGUI.drawCosmeticsButton:577`), в колонках —
карточка «Меню косметики» (`ClickGUI:732`). Всё наполнение (cape/hat/armor-секции) живёт в
`CosmeticsScreen`; в `ModuleManager` остались только модули с отдельными функциями (CustomTotem,
CustomShield, CustomWings, ChinaHat, CustomArmor и т. п.).

**GlassItem: прозрачные мечи.** Причина была в namespaces: пак перекрашивает
`textures/item/*.png` только неймспейсов `minecraft` + `visualmod` (`GlassPack:44`) — без второго
клиночные атласы мода оставались непрозрачными. Вторая часть: после перекраски клинка
(`weapon/BladeSkinLink.java:62`) вызываются `GlassPack.rebuildOverSkins():167`, иначе прозрачный
слой лежал поверх старой стрип-текстуры.

**HWCoin — встроенная функция.** Модуля нет: чип койнов рисуется из
`mixin/HWCoinChipMixin` (TAIL `HandledScreen.render`) и не показывается ни в ModuleManager, ни в
GUI. Условия: заголовок контейнера содержит «аукци/лот/торг/bid/auction/shop/магазин», в имени или
lore предмета не меньше 5 цифр. Курс — `Endpoints.COINS?limit=5`, кэш 60 с, тред `hwc-coin`.

**TNT: таймер, радиус, предупреждение.** Ядро — `util/TntWatch`: фитиль читается из
`TntEntity.getFuse()`, а радиус выучивается из `ExplosionS2CPacket`
(`mixin/ClientPlayNetworkHandlerMixin:72`), матчинг взрыва с наблюдением ≤3.5 блока, TTL 2 с.
Кастомный динамит опознаётся по translation key / имени сущности (хинты `tnt`, `динамит`,
`dynamite`, `бомба`, `взрыв`; minecart/лодка — в чёрном списке), невыученный фитиль считается от
`age` + `fuseByKind`. Выученное складывается в `visualmod_configs/tnt_watch.json`
(`{"radius":{kind:n},"fuse":{kind:ticks}}`). `TntTimer` показывает `%.2fs` над сущностью
(до 64 блоков), `TntRadius` берёт радиус из `TntWatch.radiusFor()` — то есть и у кастомного TNT.
Новая встроенная функция `util/TntWarning`: плашка с миниатюрой TNT и `%.1fs` над хотбаром
(`y = height-42`, радиус 20 блоков, пульс 2/3.5/6 Гц), рисуется из TAIL-инъекции в
`InGameHud.render`, настроек не имеет.

**RGB.** Перед полем Bind появился чип «rgb» (`ClickGUI.drawRgbChip:880`): радужный акцент модуля
в GUI и в HUD (`util/Rgb`, цикл 6 с, насыщенность 0.72), состояние — набор имён модулей в
`visualmod_configs/rgb.json`. Скорость/фазу не настраиваем.

**PotionStatus.** Ванильный столбик иконок эффектов больше не рисуется:
`mixin/StatusBarHideMixin` отменяет `InGameHud.renderStatusEffectOverlay`, пока модуль включён.
Остаются свои иконки (атлас `textures/mob_effect/<имя>.png`, заглушка
`visualmod:textures/hud/missing.png`) и настройки «Accent Color»/«Fill Style».

**HolyItems: счётчик до конца эффекта.** Модуль сам ведёт список применений и рисует таймер
билбордом в точке применения (центр блока, сдвинутый вверх на полблока). Каналов четыре: своя рука
(`ClientPlayerInteractionManagerMixin.interactItem`/`interactBlock` → `HolyItems.onLocalUse`),
чужая рука (уменьшение става при том же предмете либо `handSwinging` у последнего става),
именованные сущности, выброшенные предметы (по умолчанию выключены, только лог). Дедуп: тот же
предмет, <3 блоков, <1500 мс. Длительность: `durationSec` в конфиге `holy_items.json` (0 = глобальный
слайдер «Effect Seconds», по умолчанию 15 с) — настоящих значений сервера мы не знаем.

**InterfaceHUD вобрал GamemodeDisplay, ArrayList и PacketMeter.** Модулей больше нет (файлы удалены,
регистрации в `ModuleManager` сняты), вместо них части: «Gamemode» (иконка `eye`, режим + опция
«Gamemode Abilities»), «ArrayList» (иконка `list`, число включённых модулей + выпадающий список),
а чип «Packets» теперь показывает `N п/с ±jitter ≈loss%` и краснеет при зависании потока. Ядро
счётчика — `util/PacketStats`, панель списка — `util/ModuleList` (чип рисуется общим потоком
InterfaceHUD, панель привязана к его замеренному прямоугольнику и масштабируется вместе с HUD).
Кнопки обеих новых частей по умолчанию выключены, включаются в настройках InterfaceHUD.

**Что проверять в игре (новые пункты).**
11. **Feature control**: заход на HW — в GUI баннер «⏳ Сервер проверяет права», скрытые модули
    не включаются ни биндом, ни конфигом; после ответа `[LiteAPI] Ответ принят, скрыто модулей: N`.
    Быстрый reconnect (менее 11 с) не должен порождать второй запрос. На сервере без протокола —
    ровно 3 таймаута и `[LiteAPI] Сервер не объявил канал … контроль функций не применяется`,
    после чего всё возвращается. Ответ без `payload.blocklist` ничего не разблокирует.
12. **SlotsSwitch**: задать пару «E → 2», включить — в «Настройках управления» бинды
    поменялись, выключить — вернулись байт в байт, `slots_switch.json` очищен. Вторая/третья пары
    работают независимо.
13. **Косметика**: в GUI категория COSMETICS — одна кнопка, открывает меню; плащ/шапка/броня
    настраиваются там, в списке модулей лишних строк нет.
14. **GlassItem**: меч со включённой BladeSkin-перекраской становится прозрачным (и сразу после
    смены цвета клинка); руки — отдельная галка.
15. **HWCoin**: на аукционе/в магазине чип койнов внизу экрана, в обычном сундуке — нет.
16. **TNT**: поджечь ванильный TNT и кастомный динамит; прислать строки
    `[VisualsMod] TntWatch: …` и содержимое `visualmod_configs/tnt_watch.json` — по ним видно, чем
    HW представляет кастомный динамит и выучились ли фитиль/радиус. Таймер и сфера должны
    появляться у обоих.
17. **TNTWarning**: динамит в ≤20 блоков — значок над хотбаром с обратным отсчётом; дальше 20 — пусто.
18. **RGB**: чип «rgb» перед Bind у любого модуля; после перезапуска состояние сохранено
    (`rgb.json`), акцент мигает и в GUI, и в HUD-панели модуля.
19. **PotionStatus**: ванильный столбик иконок исчез только при включённом модуле; при выключенном
    возвращается.
20. **HolyItems**: включить модуль, применить стан/трапку/взрывную трапку на HW и прислать строки
    `[VisualsMod] HolyItems: …` — по ним видно, какой канал реально сработал (своя рука / чужая
    рука / сущность / дроп) и какие имена предметов отдаёт сервер.
21. **InterfaceHUD**: части «Gamemode» и «ArrayList» включаются в настройках, таскаются по
    отдельности (Ctrl+перетаскивание), чип «ArrayList» по клику закрепляет список, клик по строке
    открывает ClickGUI на модуле, колесо скроллит список; чип «Packets» краснеет при зависании.
    Старые модули из конфига исчезли — если они были включены, их функции теперь дают эти части.

Гейт после волны: javac — 0 ошибок (436 файла), аудит — 153 хука, 1 ложная запись `NOJAVAP`.

### Добор к волне 4: язык, кодировка, отдача

**18 модулей были без русских строк.** Проверка по дереву (`registered=140`, `with_super=139`,
`lang_entries=140`) дала 18 ключей, которых нет в `Lang`: `AirCut`, `BackSword`, `ChannelLink`,
`ClashSparks`, `CosmeticReaction`, `CritHeat`, `CritStar`, `DeathWaypoint`, `DuelBoard`, `DuelRing`,
`ExecuteMark`, `Finisher`, `MaceSlam`, `PortalCinema`, `ReachGhost`, `ServerProfiles`, `SparksTrail`,
`StrikeRune`. В RU-режиме `Lang.name()` для них возвращает английский ключ, поэтому в GUI они и
писались латиницей. Дописаны `put(...)`-пары (блок `Lang` заканчивается ими, порядок внутри блока
общую сортировку нарушает — правится одним движением, если мешает). Названия подбирались по
английскому описанию из `super(...)`: «Разрез воздуха», «Меч за спиной», «Канал энергии»,
«Искры блока», «Реакция косметики», «Нагрев от критов», «Звезда крита», «Метка смерти»,
«Табло дуэли», «Круг дуэли», «Метка добивания», «Добивание», «Удар махи», «Кинопортал»,
«Призрак дистанции», «Профили серверов», «Искры по взмаху», «Руна удара». Ключи конфиков,
`HudPosition` и `HudIcons` не затронуты — они привязаны к английскому имени.

**Кодировка сборки.** В `build.gradle` нигде не задавался `options.encoding`, то есть javac брал
кодировку ОС; на JDK 21 это UTF-8 и русские строки в jar живые, но на сборке со старым JDK или
другой локалью весь `Lang` превратился бы в кракозябры. Добавлено
`tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8' }`, офлайн-гейт теперь тоже
прогоняется с `-encoding UTF-8`.

**Два факта про дерево, которые всплыли при сборке архивов.**
- `def mcVersion = (findProperty('mc') ?: '1.21.4')` — в этом дереве дефолт всё ещё `1.21.4`,
  хотя исходники требуют yarn `1.21.11+build.6`. Собирать надо явно: `gradlew remapJar -Pmc=1.21.11`
  (это же написано в README отдаваемого архива). Дефолт не менялся — решение за тобой.
- `src/main/java/resources/` (14 json копий `*_spear` из `assets/minecraft`) в сборку не попадает:
  `sourceSets.main.resources` смотрит в `src/main/resources`, а там этих файлов нет. Каталог
  датирован 2026-10-02 и к этой волне отношения не имеет, из архивов исключён, в дереве не тронут.

**Отдача волны 4** (оба архива пересобраны после правок `Lang` и `build.gradle`):
`C:\work\HolyVisuals-1.21.11-src-full.zip` — полное дерево с `PORT-1.21.11.md` внутри,
`C:\work\HolyVisuals-1.21.11-github.zip` — для публичного репозитория: корень без обёртки,
сгенерированный из исходников `README.md` (139 модулей по категориям + встроенные функции),
`.gitignore`, `gradle/wrapper/gradle-wrapper.jar`, без `*.class`, без `PORT-1.21.11.md`,
рантайм-скрипты `assets/visualmod/browser/*.ps1` оставлены.

### Краш входа в мир (лог 20:59): `CallbackInfo` вместо `CallbackInfoReturnable`

`disconnect-2026-10-31_20.59.02-client.txt`: `Mixin transformation of net.minecraft.class_636 failed`,
ниже — `InvalidInjectionException ... @Inject::visuals$useItem(...CallbackInfo)V! CallbackInfoReturnable
is required!`. `class_636` = `ClientPlayerInteractionManager`, а грузится он на пакете
`clientbound/minecraft:login` — поэтому не пускало и в одиночную, и в сервер: мод падал ещё до
появления мира, а не на конкретном сервере.

Виновник — новые `@Inject` от HolyItems в `ClientPlayerInteractionManagerMixin`. По merged-джару
1.21.11 (`javap net.minecraft.client.network.ClientPlayerInteractionManager`):
- `interactItem(PlayerEntity, Hand)` → возвращает `ActionResult`,
- `interactBlock(ClientPlayerEntity, Hand, BlockHitResult)` → тоже `ActionResult`,
- `attackEntity(PlayerEntity, Entity)` → `void`.

Значит у первых двух хэндлеров обязан быть `CallbackInfoReturnable<ActionResult>`, а у `hit()` —
обычный `CallbackInfo`. Оба написаны с `CallbackInfo`; Mixin валидирует это на фазе APPLY и
останавливается на первом невалидном инжекте, поэтому в логе видна только `useItem`, хотя
`useOnBlock` был сломан identично. `require = 0` от этого не защищает: он разрешает «таргет не
найден», а не «таргет найден, но хэндлер ему не подходит».

Правка: оба хэндлера переведены на `CallbackInfoReturnable<net.minecraft.util.ActionResult>`
(+ импорт), логика `HolyItems.onLocalUse(hand)` не тронута. Проверка по всему дереву аудитом
ничего больше не нашла.

**Дырка в гейте, закрыта.** Прежний `check_inject` сверял только состав и порядок аргументов цели,
а тип `Callback`/`CallbackInfoReturnable` считал деталями стиля — и пропускал. Добавлены проверки
`CB-RET` (цель нет-void, хэндлер берёт `CallbackInfo`) и `CB-VOID` (цель `void`, хэндлер берёт
`CallbackInfoReturnable`). Самопроверка: копия дерева с прежним сломанным файлом даёт ровно
`CB-RET ... :50` и `CB-RET ... :57` (`hooks=153 problems=3`, третья — обычная ложная `NOJAVAP`).
Рабочее дерево после правки: **javac 0 ошибок (436 файла), аудит 153 хука / 1 ложная запись**.
Оба архива пересобраны с фиксом (21:06).


### Feature Control: разбор протокола (ревью от 01.11, 4 пункта)

Сервер отвечает по каналу `liteapi:feature-control` так:
- **ответ на запрос** = `{id, ok, payload.blocklist}` — поля `method` в нём **нет**;
- **push** = `{event, payload.blocklist}` — поля `id`/`method` в нём **нет**.

Прежний `LiteApi.handle` требовал `id` И `method` одновременно, поэтому не применялся ни
ответ, ни push: список не мог заработать в принципе. Правки (все в `C:\work\vm11`):

1. `api/LiteApi.java:211-248` — классификация по протоколу: сообщаемся только при наличии
   `id` или `event`; `event` → push; `method` проверяется лишь когда он пришёл
   (`method != null && !METHOD.equals(method)`), поэтому нормальный ответ без `method`
   больше не отбрасывается. Далее сверка `id` с `pendingId`, затем `ok`, затем `payload`.
   Ответы и push свёрнуты в один `applyList(root, what)` (`:252-266`) — `what` попадает в лог,
   поэтому видно, чем именно применился список.
2. Пункт 2 закрыт той же веткой `event != null` (`:224`) — раньше push отсекался до чтения списка.
3. `util/BindRegistry.java` — `setBind(...)` теперь возвращает `boolean` и отказывает, когда
   модуль неизвестен или `isHidden()`. Гейт стоит в реестре, а не в трёх вызывающих местах:
   `gui/ClickGUI.java` и `hud/BindsHUD.java` скрытые модули и так не показывают, а
   `mixin/ChatCommandMixin.java` (`.bind add`) различает «не найден», «сейчас скрыта — бинд
   назначить нельзя» и успех только при `true`.
4. `api/LiteApi.java:90-97` — `onJoin()` больше не запускает контроль на чужом сервере:
   `util/EventGPS.onHolyWorld()` (новый публичный метод, та же нормализация адреса, что и у
   метки ивента) решает, применять ли контроль. На не-HW адресе — `onRemoteServer = false`,
   info-лог с адресом, ничего не скрывается. Раньше на сервере с объявленным каналом, но без
   API, модули скрывались до 3 попыток, а при молчащем канале — бессрочно.

Проверка — не чтением, а исполнением: `C:\hvchk\LiteProto.java` вызывает через рефлексию приватный
`LiteApi.handle(String)` на реальных байтах класса и сверяет приватные поля состояния
(`onRemoteServer/pendingId/haveList/verifiedServer/noControl`). 12 кейсов (ответ без method,
ответ со старым method, push, push с не-строкой, пустой список, чужой id, `ok=false`,
сообщение без id и event, неизвестный method, ответ без `ok`, ответ без `blocklist`,
сообщение вне HW) → `C:\hvchk\lite_proto.txt`: **pass=12/12**.
Гейты: javac 0 ошибок (436 файла, `out_p40`), `audit2.py` → 153 хука / 1 ложная `NOJAVAP`.

Риск, который нужно знать: гейт по адресу (пункт 4) означает, что вход на HolyWorld по голому
IP или по неродственному хосту воспринимается как чужой сервер — контроль функций там не
применится. По строке в логе это видно; ниже добавлен фолбэк по бренду сервера, который этот риск закрывает.

### Кик «Connection reset» после логина (логи 06:39-06:40) — не мод

Три отчёта подряд: `java.net.SocketException: Connection reset` в NETTY-чтении (CLIENTBOUND,
протокол play). В стеке нет ни одного кадра мода — это закрытый сокет, а не исключение из нашего кода.

Что видно из `logs/latest.log`:
- `dob.FunnyMC.net` (06:39:05 → reset 06:39:07): сервер пишет «Идет проверка…» и «Решите пазл,
  поворачивая отдельные картинки», затем `Failed to retrieve profile key pair: Status: 401`
  (аккаунт не лицензирован) → сброс. Запроса LiteApi в этой сессии не было вообще: `nextSendAt`
  из прошлой сессии приходился на 06:39:09, кик случился раньше.
- `play.saturn-x.space` (бренд `SATURN-X <- NanoLimbo`): «Проверка пройдена», потом лимбо
  настойчиво требует «Введите команду /ʟ [пароль]» — клиент 21 и 40 с не вводит пароль, и сервер рвёт сокет.
  Наш канал при этом `канал объявлен сервером: false`, т.е. сервер с нами не говорит.
- `Connection reset` на самом HolyWorld был 2026-10-18 и 2026-10-20 (`HolyWоrld (Proxy 12-1/14-1)`) —
  задолго до LiteApi. Значит симптом принадлежит связке «оффлайн-аккаунт + анти-бот/пароль-лимбо +
  прокси», а не моду.

Что мод делал неправого и уже исправлено этим паком: на чужих серверах он слал `checkFeatures`
каждые 11 с и до подтверждения прятал модули. Гейт по HolyWorld (пункт 4 ревью) это убирает:
на FunnyMC/Saturn-X мод теперь не отправляет ни байта.

### Фолбэк по бренду сервера (уточнение к пункту 4)

`javap` по `net.minecraft.client.network.ClientCommonNetworkHandler` даёт `public String getBrand()` —
бренд лежит в `brand` и заполняется из `BrandCustomPayload` на config-фазе, то есть к моменту
`ClientPlayConnectionEvents.JOIN` он уже известен. (То, что в прошлом ответе я назвал бренд
недоступным, — ошибка: я смотрел `ClientPlayNetworkHandler`, `ServerInfo` и `ClientPlayerInteractionManager`,
а аксессор живёт в общем родителе.)

`EventGPS.onHolyWorld()` = адресный гейт ИЛИ бренд. Нормализация обязательна: прокси HW отдают
`HolyWоrld` где «о» — кириллическая U+043E, а на других прокси та же строка с латинской `o`
(оба варианта есть в отчётах disconnect-*.txt). Проверка `C:\hvchk\BrandTest.java` на 11 реальных
строках брендов: **pass=11/11** — оба варианта HW распознаются, `fabric`, `~~NULL~~`, `MineLegacy`,
`FunnyMC`, `SATURN-X <- NanoLimbo`, `LimboAPI -> BigAuth` отвергаются. Риск «вход на HW по IP
не применит контроль» закрыт; в лог теперь печатаются и адрес, и бренд.

Гейты после этой правки: javac 0 ошибок, аудит 153 хука / 1 ложная запись, `lite_proto.txt` 12/12.

## Пачка 2026-11-01 (вторая): конфиг «слетает под 0», FPS=1 при смене конфига, ViewModelChanger после рестарта

Три жалобы из одного сообщения разобраны по логам и починены одной механикой + одним пакетом.

### 1 и 3. Конфиг обнулялся, ViewModelChanger «под 0» после перезапуска

Цепочка была такая: вход на сервер → `LiteApi.onJoin()` → fail-closed `hideAllUnverified()` →
`ModuleManager.applyLiteBlocklist(null)` → `Module.setHidden(true)` → прежний `setHidden` вызывал
`setEnabled(false)` и `onDisable()`. Фактическое состояние «выключено» попало в `saveConfig`,
автосейв перезаписал конфиг значением «всё выключено» — бэкапы `osnovnoy` в логе это видно: on=65 → 1 → 65 → 1
(в единицу оставался только chatplus). ViewModelChanger не в `BETA_BLOCKED` и лицензия не beta,
поэтому его «под 0» — то же сокрытие, а не потеря данных: `viewmodel.json` целый, `clampField` ни при чём.
Отдельно: на чужих серверах сокрытия не было (в логе «не HolyWorld»), всё случилось на HolyWorld.

Лечится разделением «намерение / факт» в `Module`:
- `desired` — что включал пользователь, только оно пишется в конфиг (`saveConfig` читает `getDesired()`);
- `enabled` — что работает прямо сейчас = `desired && !hidden && !SafeMode.isLocked()`;
- `setHidden(...)`, `setEnabled(...)`, `resync()` только пересчитывают `enabled` через `apply()`;
- снятое сокрытие возвращает модуль включённым сам, без `.cfg restore` и без перезапуска;
- `makePermanent()` (встроенные функции) не поддаётся ни сокрытию, ни выключению.

`SafeMode` перестал затирать намерение и перечитывать конфиг с диска: он ставит `locked` и зовёт
`resync()` по всем модулям; добавлен `onDisable() → tryUnlock()`, чтобы блокировка не залипала при
любом выключении модуля, а не только по Insert.

Проверка состояний — харнес `C:\hvchk\ModuleState.java` (реальный `Module`, reflection по `SafeMode.locked`):
**pass=20/20**, файл `C:\hvchk\module_state.txt`.

### 2. FPS=1 при смене конфига

Модули-паки (CustomTotem, CustomShield, BladeSkin/GlassItem, FaithfulTextures) при включении/выключении
вызывают `RuntimePacks.profileAndReload()` → `mc.reloadResources()`. Массовый обход (загрузка конфига,
`.cfg restore`, сокрытие по блок-листу, SafeMode) делал по полной перезагрузке ресурсов на каждый шаг —
десятки перезагрузок подряд, вот и «1 FPS, пока не вернёшь прошлый конфиг». В логе `.cfg restore` в 07:45:57
совпадает по времени с пробуждением этих модулей.

Лечится пакетом: `RuntimePacks.beginBatch()` / `endBatch()` — внутри пакета профили паков переключаются,
а `reloadResources()` выполняется один раз на выходе. Пакет открыт в `ConfigManager.loadConfig()`,
`SafeMode.onEnable()`, `SafeMode.tryUnlock()`, `ModuleManager.applyLicenseTier()`,
`ModuleManager.applyLiteBlocklist()`. Вложенность допустима (счётчик), перезагрузка произойдёт на самом внешнем выходе.
В `endBatch` попадается только одна отложенная перезагрузка: `pendingReload` — флаг, а не очередь.

### 3. Залипание контроля на HolyWorld (из тех же логов)

HW отвечает за 8-13 с, прежний `TIMEOUT_MS = 5000` успевал сбросить `pendingId`, и корректный, но
поздний ответ отвергался как «не на наш запрос» — сокрытие висело всю сессию. Теперь `TIMEOUT_MS = 12000`
и id переживает таймаут (`lastQueryId`): поздний ответ и поздний push принимаются, чужой id — по-прежнему нет.

Единый флаг `Module.isProgrammatic()` заменил `ConfigManager.applying` (поле удалено): guard-ы в
ViewModelChanger, NameProtect, HWRules, CustomCursor теперь проверяют его, поэтому редактор не всплывает,
когда состояние меняет код (снятие сокрытия, загрузка конфига), и всплывает, когда включает пользователь.

Гейты после пака: javac 0 ошибок (436 файла, `C:\hvchk\out_p41`), `audit2.py` → 153 хука / 1 ложная `NOJAVAP`,
`lite_proto.txt` 15/15 (добавлены M — поздний ответ после таймаута, N — поздний push, O — чужой id после таймаута),
`BrandTest` 11/11, `ModuleState` 20/20. classpath харнессей очищен от устаревших `out_p40`/`out_bt`,
иначе тесты грузили старые классы (из-за этого `lastQueryId` сначала не нашёлся).


## Пачка 2026-11-01 (третья): ловушки — не видно значка и таймера в точке применения

Симптом: «при использовании стана / взрывной трапки и трапки не показывается, откуда было это,
т.е. нету значка звезды незера (это стан) и таймера до конца его».

### 1. Зона улетала из-за двойного сдвига матрицы

`HolyItems.drawUses()` делала `ctx.matrices().push(); translate(-cam)` и внутри этого вызывала
`HolyItems.drawArea()`, а `drawArea` **сам** сдвигает матрицу на `-cam`. Итог — бокс применения
рисовался в `center - 2*cam`, то есть за кадром. Проход `WorldRenderEvents.AFTER_ENTITIES` даёт
не сдвинутую на камеру матрицу: каждый рисунок сдвигает её сам (так делают `Prediction` и
`WorldFeatureRenderer`). Теперь `drawAreas()` только перебирает применения и зовёт `drawArea`,
никакого пред-сдвига. `Prediction:315` вызывал `drawArea` после `pop()` — там ошибки не было.

### 2. Иконки не было как класса, а подпись читаться не могла

В мире рисовался только текст `«Имя  12.3с»` биллбордом масштаба `0.03` — он терялся в тумане,
лез под геометрию и с 30 блоков не читался. Настоящий значок предмета в world-space недоступен:
`DrawContext.drawItem` живёт только в экранных слоях.

Лечение — экран слой `renderHud` на `HudRenderCallback` (тот же проект мировой точки, что у
`WaypointHud`: view = `rotate(cam.getRotation()).invert()` + `translate(-camPos)`,
proj = `getBasicProjectionMatrix(tickDelta)`). Пилюля над точкой применения: значок предмета,
имя, остаток `X.Xс`, дистанция в метрах; цвет по остатку (зелёный >4 с, оранжевый >1.5 с,
красный дальше), появление за 260 мс, при выходе точки за кадр маркер прижимается к краю.
Молчит, если модуль выключен, `Timer` выключен или HUD скрыт (`hudHidden`).

Значок — не маппинг «имя → предмет», а копия стака в момент применения (`Use.icon`): канал своей
руки отдаёт `getStackInHand`, чужой — стак из руки, снаряд — `ThrownItemEntity.getStack()`. Поэтому
звезда Незера у стана и осколок призмарина у взрывной трапки появляются сами, без правок конфига,
и для «Светильника Джейка» тоже, даже если предмет другой.

### 3. Срабатывание ловушки в полёте

Стан бьёт по месту применения, а трапку бросают: точка применения известна только когда снаряд
исчез. Новый канал
`trackLanding` (настройка `Thrown Traps`, по умолчанию ON): снаряд с именем из списка наблюдается,
счётчик ставится в последнюю его позицию. Исчезновение старше 3 с не считается (выгрузка чанка),
наблюдение снимается через 15 с. Отдельно: свой игрок теперь проверяется по руке так же, как чужие,
но только под взмахом — смена слота колесом тоже уменьшает стак и давала бы фантомное применение.
Состояние (`uses`, `hands`, `entitySeen`, `flights`) сбрасывается при смене мира и в `onDisable`,
иначе маркер переживал рестарт на старых координатах.

### Что осталось на стороне конфигов

Длительность эффекта мода не знает: `durationSec: 0` у всех записей, поэтому счёт идёт от слайдера
`Effect Seconds` (по умолчанию 15 с). Если стан на HW держится дольше — вписать число секунд в
`config/visualmod_holyitems.json` — поле `durationSec` у нужной записи — или поднять слайдер. Проверка — пункт 20 чек-листа выше
и строки `[VisualsMod] HolyItems: <имя> — применение: канал <канал>, счёт <сек>с`.

Гейты после пака: javac — 0 ошибок (436 файла, `C:\hvchk\out_p42`), `audit2.py` — 153 хука /
1 ложная `NOJAVAP`. Оба архива пересобраны (`C:\hvchk\zip_rebuild.txt`).

## Пачка 2026-11-01 (четвёртая): пустые плашки HUD и хитбоксы

### 1. Почему надписей не было вовсе

В 1.21.11 `DrawContext.drawText(...)` и `drawTextWithShadow(...)` начинаются с
`ColorHelper.getAlpha(color)`, и если старший байт равен нулю — выходят сразу, не рисуя ни
пикселя. Код, писавший цвет как `0xFFFFFF` (привычка «RGB-число»), в 1.21.4 показывал белый
текст, а здесь давал невидимый. Отсюда и «пустые плашки»: прямоугольник и обводка рисуются
`fill`-ом, где альфа-ноль — это валидный прозрачный цвет, а подпись рядом пропадает.

Лечено точечно по всем найденным местам (`C:\hvchk\alpha_scan2.py`, после правок — 0 попаданий
в аргументе цвета текста): `ChatHudEditMixin` (плашки, подсказки, тулбар, части InterfaceHUD),
`EventNotification`, `ChatAnimations`, `Keystrokes`, `FriendsScreen`, `FunTimeScreen`,
`HolyWorldScreen`, `render/CoordinatesComponent`, `render/TargetHUDComponent`,
`render/WatermarkComponent`. Плюс страховка в самих хелперах: `HudStyle.opaque()` подставляет
`FF` в старший байт, и он же стоит в `HudStyle.drawInfoPanel`/`drawIconInfoPanel` (значения
виджетов) и в `HudIcons.draw` (иконки).

Вне дефекта: `Style.withColor(int)` и `TextColor.fromRgb(int)` — это 24-битный RGB, там
шестизначные числа корректны (`HolyBadge`, `TabSelf`, `NametagStyle`), а `WelcomeBanner` и
`HolyIntroOverlay` переносят цвет через свою функцию альфы.

### 2. Модули, которые «не перемещаются»

Категория HUD насчитывает 34 модуля, а `HudRenderer.renderModule()` — цепочка из 18 `instanceof`
веток, 17 из которых про HUD (восемнадцатая, `TabGUI`, относится к PLAYER). Остальные рисуют себя
сами (своим колбэком или миксином) и в точку из `HudPosition` не
встают: `BlurGUI`, `GUIAnimations`, `GuiStyle`, `ClickGUI`, `AspectRatio`, `ChatRules`,
`DeathCard`, `DuelBoard`, `DuelRing`, `ExecuteMark`, `KillFrame`, `KillStreakBanner`, `MusicDrag`,
`TotemCounter`. В редакторе HUD для них всё равно рисовалась плашка — пустая, неподвижная и
перехватывающая клики. Это и было «нету надписей, просто пустые плашки» во второй половине.

Правка: `renderModule` возвращает `boolean`, цикл помечает пройденные модули в `HudRenderer.MOVED`,
`HudRenderer.movable(name)` — единый источник правды (наполняется самим рендером, поэтому не
разъезжается со списком). `ChatHudEditMixin.visuals$editable()` пускает в редактор только
`movable` плюс тех, кто сам читает `HudPosition`: `InterfaceHUD`, `SwapFlash`, `Scoreboard`.
Гейт стоит во всех четырёх циклах (плашки, захват драга, клик, «другие»), поэтому лишние
прямоугольники больше не рисуются и не мешают кликам.

### 3. Раздутые хитбоксы

`HudRadius.note()` объединял все прямоугольники модуля подряд, включая далёкую декоративную
плашку: у `TargetHUD` это метка у прицела, и хитбокс растягивало на пол-экрана. Теперь первый
прямоугольник — якорь, а следующий присоединяется только если прилегает (зазор `GAP = 10` px).

### 4. Скорборд: плашка теперь равна панели

`ScoreboardEditMixin` лишь сдвигал ванильную панель на фиксированное смещение от якоря
`(sw-140, 40)`, поэтому плашка редактора (размер подписи) не совпадала с панелью, а тянуть было
неудобно. `ScoreboardTextMixin` redirect'ит ванильные `fill` и пишет прямоугольник ДО смещения в
`ScoreboardEdit`: `noteRect()` копит его по кадрам, `beginFrame()` в начале кадра объявляет
прошлый замер текущим. `delta()` = точка из `HudPosition` минус левый край панели, `bounds()`
возвращает тот же прямоугольник со смещением — редактор рисует плашку реального размера, и драг
не уводит панель (смещение пересчитывается от неё же). На первом замере позиция пересобирается
из «смещения от якоря» в «координату левого края» (`visuals$rebase`), иначе панель дёрнулась бы на
разницу якорей. До первого замера (сайдбара нет на сервере) работает прежний якорь.
Масштаб на скорборд не применяется — ванильный миксин его не знает, поэтому `visuals$box` для
него возвращает сырые границы.

Гейты после пачки: javac — 0 ошибок (436 файла, `C:\hvchk\out_p43b`), `audit2.py` — 153 хука /
1 ложная `NOJAVAP`, `hmap.py` — 52.


## Пачка 5 (2026-11-01, финал 1.5): новый HUD-стиль, классический ClickGUI, InventoryCheck, значок по HWID

Релиз 1.5 закрывает визуальную часть: перерисованы экраны, HUD приведён к одному стилю,
появился значок-логотип и модуль `InventoryCheck`. Все правки — в дереве `C:\work\vm11`
(1.21.11); эталон 1.21.4 не трогали.

### Экраны

- **Главное меню** (`MainMenuButtonMixin`): три широкие тёмные кнопки со значками и ряд из
  четырёх маленьких квадратных. Скругления и иконки рисуются в самом миксине, ванильные
  `ButtonWidget` остаются под ними — клики и раскладка не менялись.
- **ClickGUI** (`gui/ClickGUI.java`, `module/modules/hud/ClickGUI.java`): режим «колонки» заменён
  на классический список в стиле Phaze — широкие секции категорий, акцентная полоса у активного
  модуля, настройки раскрываются в той же панели.
- **Косметика** (`gui/CosmeticsScreen.java`): при открытии сразу включается вид от третьего лица
  (F5), панель слотов уезжает вправо, модели показываются без предварительного выбора.
- **Звук открытия GUI** (`GUIAnimations.playOpen()`): по Shift играем выбранный звук из модуля,
  а если GUIAnimations выключен — тихий `BLOCK_AMETHYST_BLOCK_CHIME`. `Sound.NONE` — явная тишина.

### HUD

- `HudStyle.ACCENT = 0xFF8B5CF6` — единый фиолетовый акцент; панели — тёмные пилюли
  (`HudStyle.pill(h)`, `drawPanel`, `glassPill`), без прежней окантовки.
- `HotbarHUD`: девять круглых слотов (у выбранного — кольцо акцента), левая рука — такой же
  круглый слот слева.
- `HudPosition.MAX_SCALE = 5f` — верхний предел размера любого HUD-модуля (было меньше).

### InventoryCheck (новый модуль, UTIL)

`util/InventoryCheck.java` + `module/modules/util/InventoryCheckModule.java`:
модуль берёт игрока под прицелом (`EntityHitResult`, иначе луч 8 блоков), шлёт
`/invsee <ник>`, снимает содержимое из открывшегося `HandledScreen` (слоты чужого инвентаря,
без `PlayerInventory`), сразу закрывает экран и рисует панель над головой цели через
`HudRenderCallback` (9 колонок, снимок живёт 5 с, запросы не чаще 1.5 с).
Если экран не открылся за 2.5 с — сообщение «Данная функция работает только от доната драгон»
и модуль перестаёт слать запросы. В PVP-режиме (`PvpSafe.inPvp()`) модуль выключает себя сам:
`/invsee` там недоступен. `InventoryCheck.init()` защищён от повторной регистрации колбэка.

### Значок и макросы

- `HolyBadge`: новый глиф-логотип в шрифте `visualmod:hw_badge`
  (`assets/visualmod/font/hw_badge.json` + `textures/font/hw_badge.png`), ставится перед ником
  владельцу лицензии по HWID (аккаунт не важен) и перед ником других игроков с этим модом —
  в табе и над головой вблизи. Пробел после глифа берётся дефолтным шрифтом.
- `KeyNames`: в `CHAR_KEYS` добавлены все символьные клавиши (бэктик, `~`, `-`, `=`, `[`, `]`,
  `\`, `;`, `'`, `,`, `.`, `/` и их Shift-варианты) — макрос можно повесить на бэктик и прочее.
- Удалены `SlotsSwitch` и `Tab+`: модуль и миксин вычищены из `ModuleManager` и
  `visualmod.mixins.json`, вместе с их `.class`-файлами в дереве.

Гейты после пачки: офлайн-javac — 0 ошибок (`C:\hvchk\javac_p51.txt`), `audit2.py` —
152 хука, 1 ложная `NOJAVAP` по joml (`C:\hvchk\audit_p51.txt`).


## Фикс1 (2026-11-01): краш сборки — кэш Loom, а не код

`./gradlew assemble "-Pmc=1.21.11"` упал на этапе конфигурации, до компиляции мода:

```
Failed to setup Minecraft, net.fabricmc.loom.util.FileSystemUtil$UnrecoverableZipException:
C:\work\vm11\.gradle\loom-cache\minecraftMaven\net\minecraft\minecraft-merged-2ae02fda0f\...\minecraft-merged-2ae02fda0f-1.21.11-...-v2.jar
BUILD FAILED in 2m 19s
```

Причина: предыдущий запуск gradle был убит во время пересборки loom-кэша (в логе —
`Previous process has disowned the lock due to abrupt termination`). Merged-джар
`minecraft-merged-2ae02fda0f` остался недозаписанным, и Loom при проверке CRC выкинул
`UnrecoverableZipException`. Ни один `.java`/`.json` мода в краше не участвует. Вторая строка
лога `NoSuchMethodError: TcpOutgoingConnector.connect(Address)` в `DaemonUtils.stopWhenIdle` —
несовместимость Loom 1.17.21 с внутренним API Gradle 9.8.2 на ветке «остановить демон при
ошибке»; на результат сборки не влияет, но шумит.

Что проверено после краша:
- merged-джар сейчас цел: 29 069 записей, `testzip()` → None (все CRC сошлись), 34 805 206 Б,
  перезаписан 11-01 18:53:38 (пересборка завершилась успешно); рядом легитимный `.backup`
  от 10-27 — тоже валидный, его не трогать.
- офлайн-гейты по этому же джару зелёные: javac — 0 ошибок, 435 файлов
  (`C:\hvchk\javac_p52.txt`), `audit2.py` — 152 хука и 1 ложная `NOJAVAP` по joml
  (`C:\hvchk\audit_p52.txt`). То есть исходники пачки 5 к крашу отношения не имеют.
- диск: 21 ГБ свободно — это не прошлогодний случай «remapJar падает из-за ENOSPC».
- осколок убитого процесса `zipfstmp10616582729441848573.tmp` (34 МБ) убран из каталога
  merged-джара в `C:\work\vm11\.gradle\loom-cache\_stale\` — не удалён, при необходимости
  можно вернуть на место.
- ресурсы пачки 5 вычитаны на всякий случай (краш был до них): 53 JSON-файла в
  `src/main/resources` парсятся без ошибок; в `visualmod.mixins.json` 104 записи, под каждую
  есть `.java` в `com/visualmod/mixin/` и ни одного лишнего — после удаления `SlotsSwitch`
  и `Tab+` висячих ссылок не осталось; `fabric.mod.json` → id `visualsmod`, version `1.5`.

Как собрать:
1. `./gradlew assemble "-Pmc=1.21.11"` — без `--no-daemon` (см. раздел
   «Фикс2» ниже: именно с `--no-daemon` следующий запуск упёлся в новый краш).
2. Если `UnrecoverableZipException` повторится — удалить каталог
   `C:\work\vm11\.gradle\loom-cache\minecraftMaven\net\minecraft\minecraft-merged-2ae02fda0f`
   и запустить сборку снова: Loom соберёт merged-джар с нуля (первые 2–4 минуты). Остальной
   loom-кэш не трогать.
3. Не прерывать сборку (Ctrl+C, закрытие окна, перезагрузка) пока пишется loom-кэш — именно
   это оставило битый джар и `.tmp`-осколок.

Код правки не потребовал: дамп изменений этого коммита — только этот раздел.


## Фикс2 (2026-11-01): второй краш сборки — это тот же loom-кэш, но стадия ремапа модов

Запуск `./gradlew assemble "-Pmc=1.21.11" --no-daemon` (19:18:44, упал в 19:20:04):

```
Previous process has disowned the lock due to abrupt termination.
Found existing cache lock file (ACQUIRED_PREVIOUS_OWNER_DISOWNED), rebuilding loom cache.
Cannot remap modifiers because it does not exist in any of the targets [] or their parents.
Cannot remap modifiers because it does not exist in any of the targets [] or their parents.
> Failed to setup Minecraft, java.lang.RuntimeException: Failed to remap 46 mods
2: Task failed with an exception.
Failed to release lock on Build Output Cleanup Cache (C:\work\vm11\.gradle\buildOutputCleanup)
> Запрошенную операцию нельзя выполнить для файла с открытой пользователем сопоставленной секцией
BUILD FAILED in 1m 22s
```

Исходники мода снова ни при чём: офлайн-гейты не менялись и остаются зелёные (javac 0 ошибок
/ 435 файлов, `audit2.py` — 152 хука и 1 ложная `NOJAVAP` по joml). Совет `--no-daemon` из
«Фикс1» оказался неверным — именно с этим флагом краш и случился, шаг 1 там исправлен.

### Что значит каждая строка (проверено по исходникам Loom 1.17.21 и tiny-remapper 0.14.0)

- `Failed to remap 46 mods` — обёртка из `ModProcessor.processMods` (`ModProcessor.java:96`):
  `catch (Exception e) → RuntimeException("Failed to remap %d mods", e)`. Это **не диагноз, а
  обёртка** любого исключения этапа `remapJars`. `OutOfMemoryError` в неё не попадает
  (ловится только `Exception`), значит вышло исключение работы с файлами, а не нехватка кучи.
- 46 — ровно сет fabric-api под 1.21.11: в
  `C:\work\vm11\.gradle\loom-cache\remapped_mods\remapped\net\fabricmc\fabric-api` 96
  каталогов модулей, из них 46 с суффиксом `-16c8840d` (наш набор) и 50 с `-46549a5a`
  (остались от сборки под 1.21.4). Наших `.java` там нет.
- Две строки `Cannot remap modifiers ... targets []` — **предупреждение**, а не причина
  падения: константа `Message.NO_MAPPING_RECURSIVE` (`Message.java:26` tiny-remapper)
  печатается через `getLogger().warn(...)` в mixin-расширении (`NamedMappable.java:66`,
  `ConvertibleMappable.java:71`), и при отсутствии маппинга просто возвращает исходное имя.
  Это про селектор `modifiers` в чужих mixin-конфигах из fabric-api; на результат ремапа не
  влияет.
- `Failed to release lock on Build Output Cleanup Cache … файл с открытой пользователем
  сопоставленной секцией` — Windows `ERROR_USER_MAPPED_FILE`: файл держит живой процесс.
  Прямое подтверждение, что в момент сборки кэш был занят вторым JVM.

### Почему каждый раз крашится по-разному

По логам демонов `C:\Users\Максим\.gradle\daemon\9.8.2\daemon-*.out.log` сегодня четыре
падения подряд на одном и том же месте — на настройке Loom:

| время | JVM | что написано |
|---|---|---|
| 18:49:38 | демон 29492 | `Failed download after 3 attempts` для `loom.minecraftJarConfiguration` |
| 18:51:21 | демон 29492 | `UnrecoverableZipException` по merged-джару (это «Фикс1») |
| 19:16:40 | демон 29492 (из IntelliJ) | `Failed to setup Minecraft, java.nio.file.ClosedFileSystemException` |
| 19:18:44–19:20:04 | одноразовый 1360 (терминал) | `Failed to remap 46 mods` + `ERROR_USER_MAPPED_FILE` |

Цепочка: сеть дала сбой → Loom не докачал джары Minecraft → при падении конфигурации Loom
пишет в свой lock-файл `.gradle\loom-cache\.<hash>.lock` строку `disowned`
(`CompileConfiguration.disownLock`, стр. 445–455) → следующий запуск читает `disowned`,
печатает «rebuilding loom cache» и включает `refreshDeps` (`CompileConfiguration.java:116–121`)
→ в этом режиме `remapJars` **сначала удаляет** все 46 готовых джаров (`ModProcessor.java:209`
`Files.deleteIfExists(getRemappedOutput(info))`), потом перезаписывает их и по каждому ещё
несколько раз трогает файл (замена access-widener через `ZipUtils.replace`, `stripNestedJars`,
перезапись `MANIFEST.MF`, `copyToCache`/`copyToMaven`). Любой из этих шагов на Windows
падает, если файл одновременно держит другой JVM, — отсюда три разных симптома при одной
причине. Второй JVM — демон 29492: он жив с 18:49 (`tasklist`: java.exe 29492, `-Xmx1G`),
получал сборку из IntelliJ в 19:16:40 и держит маппинги кэша. Плюс память: в 19:18:44 сам
Gradle записал `786940313 physical memory requested, 443232256 free` — попросил 750 МБ,
свободно было 423 МБ из 7,33 ГБ.

### Состояние кэша сейчас (проверено после краша)

- lock-файл Loom в `.gradle\loom-cache` отсутствует → следующий запуск получит
  `ACQUIRED_CLEAN` и **не** будет пересобирать кэш целиком.
- Все 46 модулей 1.21.11 на месте: в каждом каталоге есть и `.jar`, и `.pom` (неполных 0);
  последние перезаписи — 19:19:48–19:20:02, то есть работа фактически сделана, упало на самом
  хвосте этапа.
- merged-джар 1.21.11 цел (29 069 записей, `testzip()` → None — см. «Фикс1»).
- Проверка по исходникам, что переремап не нужен: `ModConfigurationRemapper.java:214–215`
  берёт в переработку только `refreshDeps || isCacheInvalid(...)`, а
  `SimpleModDependency.isCacheInvalid` — это `!maven.exists(variant)` (стр. 51–53). Джар и пом
  есть у всех 46.

### Как собрать (по порядку)

1. `./gradlew --stop` — убрать демон 29492 и любые другие. Проверить
   `tasklist /FI "IMAGENAME eq java.exe"`: должен остаться только `javaw.exe` лаунчера
   TLauncher. Если 29492 вернулся — закрыть IntelliJ IDEA.
2. Освободить память до ~2 ГБ свободных (лишние окна Chrome; игру не запускать параллельно
   со сборкой).
3. Одна сборка за раз, без `--no-daemon` и не из двух мест одновременно:
   `./gradlew assemble "-Pmc=1.21.11" --stacktrace`. `--stacktrace` нужен, чтобы при повторе
   было видно настоящее исключение (`Failed to remap: <мод>` + тип IO-ошибки) — без него
   Gradle печатает только обёртку, и в этих четырёх логах `Caused by` нет ни разу.
4. Если снова `Failed to remap 46 mods` — перезагрузить машину (только так снимаются
   маппинги `ERROR_USER_MAPPED_FILE`) и выполнить пункт 3 из терминала при закрытой IDEA.
   Каталог `.gradle\loom-cache\remapped_mods` можно удалить — Loom соберёт его заново;
   `minecraftMaven` не трогать.
5. Не помешает: исключить в Avast папки `C:\work\vm11\.gradle` и `C:\work\vm11\build` —
   за один проход ремапа перезаписывается 92 файла (46 .jar + 46 .pom), и сканер реагирует
   на них как лок на файлы.

Код мода снова не изменён: правки этого коммита — только этот раздел и исправленный шаг 1 в
«Фикс1».


## Фикс3 (2026-11-01): сборка зелёная. Настоящих причин было четыре, и ни одна не в исходниках

Проверено на самой машине, два запуска подряд, 1 ноября 2026:

```
./gradlew build "-Pmc=1.21.11" -PhvSkipAuth=1 -PnoUpload
BUILD SUCCESSFUL in 54s   7 actionable tasks: 7 executed
BUILD SUCCESSFUL in 34s   (повтор тем же прогоном, тем же JVM-флагом)
build/libs/visualsmod-1.21.11.jar — 15 051 587 Б, 846 записей, zipfile.testzip() = None
```

Ни в одном из двух логов нет строк `disowned`, `rebuilding loom cache`,
`UnrecoverableZipException`, `ClosedFileSystemException`, `Failed to remap` — то есть этап
настройки Loom, на который приходилось каждое падение из «Фикс1» и «Фикс2», больше не участвует.
Логи: `C:\hvchk\build_fix3d.log`, `C:\hvchk\build_fix3e.log`.

### Что в «Фикс1» и «Фикс2» оказалось неверным

- Совет «удалить каталог `minecraftMaven\net\minecraft\minecraft-merged-2ae02fda0f`» (шаг 2
  «Фикс1») построен на ложной предпосылке. Мerged-джар был цел всё это время: 29 069 записей,
  `testzip()` → None, и рядом легитимный `.backup`. Удаление каталога не лечит ничего, а только
  заставляет Loom заново собирать 34 МБ и снова писать в то же место, где его может перехватить
  второй JVM. НЕ УДАЛЯТЬ.
- «Сборка падает из-за нехватки памяти» — тоже не диагноз. `OutOfMemoryError` не попадает в
  обёртку `Failed to remap 46 mods` (в `ModProcessor.processMods` ловится только `Exception`),
  а память в логах — следствие, а не причина.
- Совет `--no-daemon` из «Фикс1» был неверен по форме: флаг в момент запуска крашит Loom
  иначе, чем демона. Правильно — `org.gradle.daemon=false` в `gradle.properties` (см. ниже).

### Причина 1. Loom 1.17.21 не может почистить за собой на Gradle 9 — и демон отравляется навсегда

`DaemonUtils.stopWhenIdle` вызывает `TcpOutgoingConnector.connect(Address)`. Проверено `javap`
по джарам дистрибутивов, которые реально скачаны на этой машине:

```
gradle-messaging-8.14.3.jar:  ConnectCompletion connect(Address)              ← нужна Loom'у, есть
gradle-messaging-9.8.2.jar:   ConnectCompletion connect(Address, byte[])      ← только такая, одноаргументной НЕТ
```

Отсюда `NoSuchMethodError` ровно в том месте, где Loom пытается по-хорошему остановить «свой же»
демон после сбоя (`ExceptionUtil.processException` → `CompileConfiguration.lambda$run$1`).
Демон не останавливается, а вместе с ним в JVM остаются живыми `zipfs`-хендлеры Loom (он montage'ит
client+server jar через `FileSystems.newFileSystem`). Любой последующий прогон в том же демоне
падает по `ClosedFileSystemException` — этим и объясняются builds 3–6 в
`daemon-29492.out.log`, где один и тот же процесс выдавал ошибку четыре раза подряд без всякой
смены кода.

Отравленный демон 29492 снят штатным `./gradlew --stop`. Замер 1 ноября в 21:13
(`C:\hvchk\stop_daemons.log`): первый прогон — `Stopping Daemon(s)` / `1 Daemon stopped`,
второй — `No Gradle daemons are running`. Это не косметика: в `daemon-29492.out.log` тот же PID
четыре раза подряд отдавал `ClosedFileSystemException` без всякой смены кода.

Оговорка: `daemon=false` не означает, что после сборки не остаётся ни одного JVM. Из зелёных
прогонов живыми остались два (`java.exe` 22008 со start 20:44 и 18868 со start 20:50, у обоих в
командной строке `GradleDaemon` и `-Xmx2G`); `--stop` снял 18868, а 22008 пережил и первую, и
вторую команду — то есть в реестре демонов его уже нет, новые сборки он не принимает и в лечении
не участвует. Вывод «`daemon=false` даёт чистый JVM» опирается на это, а не на подсчёт процессов.

### Причина 2. Понизить Gradle нельзя — Loom требует 9.5.0

Гипотеза «вернуть 8.14.3, где у Loom'а есть рабочий путь восстановления» была проверена и
опровергнута на живом прогоне: сборка не доходит даже до компиляции,

```
No matching variant of net.fabricmc:fabric-loom:1.17.21 was found. The consumer was configured
to find ... 'org.gradle.plugin.api-version' with value '9.5.0' ... Gradle 8.14.3 ...
BUILD FAILED in 13s   (C:\hvchk\build_fix3b.log)
```

Оба файла wrapper'а уже возвращены к `gradle-9.8.2-bin.zip`. То есть лечить надо не версию
Gradle, а способ, которым сборка переживает чужие падения.

### Причина 3. `-Xmx2G` и `daemon=false` лежали не там, где Gradle их читает

В `gradle\wrapper\gradle-wrapper.properties` были дописаны две строки
`org.gradle.jvmargs=...` и `org.gradle.daemon=false`. Gradle читает эти ключи только из
`gradle.properties` (и из `-D` командной строки), в wrapper-файле они молча игнорируются.
Реальная куча задавалась в `gradle.properties`: `-Xmx1G`, что подтвердил сам демон:
`daemonOpts=-Xmx1G`. Теперь обе строки перенесены по адресу, куча 2G +
`MaxMetaspaceSize=512m` + `UseParallelGC`, и `org.gradle.daemon=false`.

`daemon=false` — главный страховочный механизм после причины 1: каждый прогон получает новый
JVM, в котором не может быть чужих zipfs-хендлеров. Плата — +15–20 с на старт, видно по 54 с
первого прогона против 34 с второго.

### Причина 4. `.gradle/` и `build/` были в git, и их держали IDEA и Avast

Файлы сборки, перечисленные в `.gitignore`, одновременно были **закоммичены** (а сам
`.gitignore` — нет: `git log -- .gitignore` пустой, он ни разу не попадал в репозиторий).
В HEAD до уборки висело 2546 файлов, из них `build/` — 980 и `.gradle/` — 325. Раз файл
отслеживается,
его сканирует real-time-защита и индексирует VCS-подсветка IDEA; оба маппят его в память, а
Loom в этот момент его перезаписывает → Windows `ERROR_USER_MAPPED_FILE`
(«Запрошенную операцию нельзя выполнить для файла с открытой пользователем сопоставленной
секцией») — ровно та ошибка, что стояла второй строкой в «Фикс2» и в падежении
`Build Output Cleanup Cache`.

### Уборка: из индекса выведено 1929 файлов, добавлено 2

| что | файлов | куда делось |
|---|---|---|
| `build/` | 980 | только из индекса, на диске целы (это кэш сборки) |
| `src/**/**/*.class` | 560 | удалены с диска — в дерево исходников их накапал прошлый прогон |
| `.gradle/` | 325 | только из индекса |
| `tools/` | 35 | вынесены в `C:\hvchk\vm11-junk-2026-11-01\tools\` |
| `.idea/` | 11 | только из индекса, IDEA продолжает работать с ними на диске |
| `Holy-visual info/` | 6 | вынесено в `C:\hvchk\vm11-junk-2026-11-01\Holy-visual-info-runtime\` |
| `vibe_images/` | 1 | AI-оригинал `bg_hv.png` переложен в `C:\Users\Public\hv_tex\vibe_images\` (дампы ассетов по нашему правилу живут там) |
| `log11.txt`, `refmap_check.json`, gitlink `HV_for_zaKK0` | 3 | `.txt/.json` вынесены в тот же junk-каталог |
| 4 пустых `Новый текстовый документ.txt`, `MinecraftClientAccessor.java.txt` (тоже 0 Б), 2 `list.txt` с перечнем модулей, `ui.lnk` | 8 | только из индекса, на диске целы; правила для них дописаны в `.gitignore` |

После уборки в индексе 619 файлов — и это ровно те, что должны быть в репозитории: `src/`,
`gradle/`, `gradlew(.bat)`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`,
`.gitignore`, `PORT-1.21.11.md`. На диске в корне при этом остались `build/`, `.gradle/`, `.idea/`,
`run/` и незакрытый `HV_for_zaKK0/` — все они теперь под `.gitignore` и в коммит не попадают.
`.java` в `src/main/java` — 434 (плюс генерируемый
`HvMarker.java` = те 435, что считает офлайн-гейт javac).

Про папку `hvgen`: папки с таким именем в дереве нет — `find . -iname "*hvgen*"` пустой.
Похожее название даёт `tools/` (генераторы `armorgen.py`, `swordgen.py`, `gen_deagle.py`,
прогонные `parsecheck/*.java` и `.ps1/.vbs`-обёртки). Всё это — инструмент проверки, в сборке
не участвует: `build.gradle`, `settings.gradle` и `gradle.properties` на них не ссылаются.
Вынесены, не удалены: можно вернуть одной командой из `C:\hvchk\vm11-junk-2026-11-01\`.

`HV_for_zaKK0\` с диска не ушёл: `mv` отдал `Permission denied`, держит живой `idea64` (PID 2656,
рядом `fsnotifier`). Это второй клон того же репозитория с **несвязанной** историей
(`16dbea2 «Распаковал мод»`, которого нет в нашем `git cat-file`), а `origin/main` в нём — устаревший
локальный ref. Пуш из такого клона перетрёт `main` целиком. Он добавлен в `.gitignore` и выведен из
индекса; чтобы убрать с диска — закрыть IDEA, потом
`Remove-Item C:\work\vm11\HV_for_zaKK0 -Recurse -Force` (в нём 42 МБ, и ни одного незапушенного коммита).

Что НЕ мусор и осталось на месте: `C:\hvchk` с офлайн-гейтами (`javac_p52.txt`, `audit2.py`,
helper'ы `javap`) — без них проверка порта вообще не делается, это рабочий инструмент, а не
побочный продукт.

Отдельно, по-честному: 14 файлов `src/main/java/resources/assets/minecraft/{items,models/item}/*_spear.json`
(копья всех семи типов, дважды) лежат в корне, которого Gradle не видит. Ресурсы берутся из
`src/main/resources`, а `src/main/java/resources` в `build.gradle` никуда не подключён — значит, в
jar они не попадают и в игре не работают. В индексе их трогать не стал: это не мой вывод проверок, а
твоя заготовка под профиль копья (#304). Если она нужна «как модель в игре» — перенести в
`src/main/resources/assets/minecraft/...` тем же `move`; если как заметка — пусть лежит, вреда нет.

### Как собирать теперь (проверено именно так)

1. Один прогон за раз, из одного места, без `--no-daemon` (он уже включён в `gradle.properties`):
   `./gradlew build "-Pmc=1.21.11" --stacktrace`
2. При `ClosedFileSystemException` ничего не удалять: просто прогнать ещё раз — новый JVM не
   наследует чужие хендлеры. Если ошибка повторится дважды, смотреть, кто держит файлы:
   `tasklist /FI "IMAGENAME eq java.exe"` (лишние JVM от IDEA) и Avast.
3. Исключить в Avast `C:\work\vm11\.gradle` и `C:\work\vm11\build` — за проход ремапа
   перезаписывается 92 файла, сканер реагирует на них как на лок. Это только админ-действие
   самого Максима, агент его не делает.
4. Каталог `minecraftMaven` не трогать никогда (см. «Фикс1»: джар был цел).

### Остался один блокер — он не в сборке, а в ключе: 401 на `:genHvSignature`

Кэш Loom больше не падает, и сборка дошла до собственной защиты мода:

```
Execution failed for task ':genHvSignature'
> HV: сервер отверг ключ сборки (HTTP 401).
    Куда стучались : https://visualmod-auth-system2.2011shakurov.workers.dev/api/admin/build
    Ключ взят из  : HOLY_ADMIN_TOKEN, длина 34 симв.
```

Проверено, что это не код и не сеть (воркер жив, ревизия актуальная: `GET /api/configs/build`
→ `{"ok":true,"v":"cfg-17.10"}`, что совпадает с `CFG_BUILD` в локальном `worker-merged.mjs`):

- `POST /api/admin/build` с ключом из окружения → 401 «Нет доступа»;
- `GET /api/admin/keys` с тем же ключом → 401;
- заведомо выдуманный ключ → тоже 401. То есть `isAdmin()` (`worker-merged.mjs:121`,
  сравнение с `env.ADMIN_TOKEN`) не проходит вообще ни с чем — секрет в проде не равен
  переменной `HOLY_ADMIN_TOKEN` на машине (или не задан/стал короче 24 символов).
- Второй воркер, `visualmod-admin.2011shakurov.workers.dev` (это `LockScreen.ADMIN_API_URL`,
  к ключу сборки отношения не имеет), на `POST /api/admin/build` отвечает `404 not found` и с
  настоящим ключом, и с выдуманным — роута там нет и проверка токена до него не доходит.
- Само значение ключа нигде не печатается и в файлы не попадает; в логах и в этом разделе есть
  только его длина.

Починка на стороне Cloudflare, её делает только Максим: сверить `HOLY_ADMIN_TOKEN` в PowerShell
(`$env:HOLY_ADMIN_TOKEN.Length`) со значением `ADMIN_TOKEN` воркера
`visualmod-auth-system2` (Settings → Secrets) и привести к одному. Вариант «ключ задали вручную в
одном окне, а сборку погнали из другого» тоже даёт такую картину: переменная уровня пользователя
(`setx`) может хранить более старый ключ, чем текущая сессия.

До тех пор годный для игроков jar собрать нельзя: `-PhvSkipAuth=1` кладёт в `HvMarker.REF` строку
`DEV`, а `HvIntegrity.verify()` на такое значение жёстко валит запуск
(«сборка сделана без ключа администратора»), и это происходит только в реальной игре — в dev-среде
Fabric проверка пропускается. Оба зелёных прогона выше были именно с `-PhvSkipAuth=1`: они
доказывают, что починен кэш и компиляция, но jar из них в руки игрокам не годится.

Изменения в коде этого раздела: `gradle.properties` (куча и флаг демона переехали по адресу),
`gradle/wrapper/gradle-wrapper.properties` (вернули 9.8.2, убраны две бесполезные строки),
`build.gradle` (сообщение об отказе ключа теперь печатает URL, HTTP-код и длину ключа вместо
«введи ключ»), `.gitignore` (он же впервые попал в репозиторий + 7 новых правил), `README.md`
(впервые лежит в репозитории) и уборка индекса. Исходники мода (`src/**.java`) не изменены
ни на строку — проверено `git diff --cached --name-only HEAD -- '*.java'`: внутри `src/main/java`
там нет ни одной буквы.

