# ArcherBC2 - Керівництво користувача

## Введення
**Додаток використовується для створення і редагуванння балістичних профілів для\
**Тепловізійних стрілецьких прицільних комплексів ARCHER.**\
Додаток використовує спеціально розроблений формат файлів `.a7p` який підтримується найновішими приладами Archer.**

## Зміст
* **[Діалог запуску](#start-dialog)**
* **[Налаштування](#app-settings)**
* **[Створення балістичного профілю](#creation-wizard)**
  * [Опис профілю](#wizard-desc)
  * [Рушниця](#wizard-rifle)
  * [Набій](#wizard-cart)
  * [Куля](#wizard-bullet)
  * [Робочі дистанції](#wizard-dist)
  * [Баллістичний коефіцієнт](#wizard-dm)
  * [Збереження профілю](#wizard-save)
* **[Редактор балістичних профілів](#profiles-editor)**
  * [Верхня панель](#top-bar-actions)
  * [Вкладки бокової панелі](#sidebar-tabs)
* **[Температурна залежність пороху](#powder-sens)**
* **[Поширені запитання](#faq)**
  * [Додаток не запусакється або не оновлюється](#app-run-issue)

## <span id="start-dialog"> Діалог запуску </span>

Під час запуску програми виберіть необхідний варіант **«Створити»** або **«Відкрити»** і натисніть **«Ок»**.
В залежності від вибору відкриється або **[Майстер створення профілю](#creation-wizard)** або **діалог вибору файлу** 

<img alt="" src="pictures/start-dialog.png"/>

## <span id="app-settings"> Налаштування </span>

* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/icon-languages.png"/> - Вибір мови
<img alt="" align="bottom" src="pictures/language-selector.png"/>

* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/actions-group-theme.png"/> - Вибір теми
<img alt="" align="bottom" src="pictures/theme-selector.png"/>

## <span id="creation-wizard"> Майстер створення балістичного профілю </span>
### При виборі опції `Створити` запускається `Майстер створення нового профілю` 
Нижче описані кроки для створення `.a7p` файлу профілю

#### <span id="wizard-desc"> 1. Опис профілю </span>
З першу треба заповнити назви профілю, патрону, кулі і натиснути `Далі`. Ця інформація буде відображатись в меню `Гвинтівки` приладу.

![](pictures/wizard-desc.png)

#### <span id="wizard-rifle"> 2. Вкажіть параметри рушниці </span>

**Вкажіть такі параметри:**
* Калібр ствола - *можна ввести вручну або скористатись кнопкою `Вибрати`*
* Крок нарізів (Твіст)
* Напрямок нарізів
* Висота прицілу

![](pictures/wizard-rifle.png)

#### <span id="wizard-cartridge"> 3. Вкажіть параметри набою </span>

**Вкажіть такі параметри:**
* Температура пороху
* Базова швидкість при вказаній температурі
* [Коефіцієнт температурної чутливості пороху](#powder-sens)

![](pictures/wizard-cart.png)

#### <span id="wizard-bullet"> 4. Вкажіть фізичні розміри і вагу кулі </span>

![](pictures/wizard-bullet.png)

#### <span id="wizard-dist"> 5. Виберіть діапазон дистанцій </span>

Оберіть діапазон робочих дистанцій відповідно до ваших потреб із запропонованих варіантів
Ці дистанції будуть доступні у при виборі поточної дистанції в інтерфейсі приладу

![](pictures/wizard-dist.png)

#### <span id="wizard-dm"> 6. Драг-модель і балістичний коефіцієнт </span>
**В наступних кількох пунктах потрібно обрати:**
* Тип Драг-моделі (G1 або G7)
![](pictures/wizard-dm.png)

* Який тип балістичного коефіцієнту використовувати - усереднений БК (Single) або мульти-БК
![](pictures/wizard-bc-type.png)

* В залежності від обраного типу БК введіть:
  * Усереднений бк
  ![](pictures/wizard-bc-single.png)
  * Або таблицю мульти-БК для різних швидкостей
  ![](pictures/wizard-bc-multi.png)

#### <span id="wizard-save"> 7. Збереження профілю </span>
Програма запропонує зберегти щойно створений профіль до файлу, оберіть місце збереження і натисніть `Save`.
На цьому етапі профіль вважається створеним і буде відкритий в [редакторі](#profiles-editor), мова про який йде в наступному розділі
![](pictures/save-dialog.png)

## <span id="profiles-editor"> Редактор балістичних профілів </span>
Редактор відкривається одразу після створення нового профілю, або при відкритті профілю із `.a7p` файлу

### <span id="top-bar-actions"> Верхня панель </span>
![](pictures/editor-top-bar.png)
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-new.png"/>
  Створити - викликає [майстер створення нового профілю](#creation-wizard)
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-open.png"/>
  Відкрити - відкриває діалог вибору файлу
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-save.png"/>
  Зберегти - зберігає зміни до поточного відкритого файлу
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-save-as.png"/>
  Зберегти як - відкриває діалог для вибору місця збереження файлу
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-reload.png"/>
  Перезавантажити - відновлює дані з поточного відкритого файлу 
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/load-zero-x-y.png"/>
  Імпорт пристрілки - дозволяє завантажити пристрілку з іншого файлу до поточного 
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-import.png"/>
  імпорт балістичного профілю з JSON 
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-export.png"/>
  експорт балістичного профілю в JSON

### <span id="sidebar-tabs"> Вкладки редактора </span>
Вкладки бокової панелі надають можливість редагування балістичних параметрів відповідно до розділів 
* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-description.png"/> Опис профілю**
  
  Тут можна редагувати та змінювати `опис балістичного профілю`, ці дані відображатимуться в меню `Гвинтівки` приладу \
  Також можна змінити `короткі назви для іконки профілю` (Параметри `Верх` і `Низ`)
  або додати нотатку до поточного профілю \
  <img src="pictures/editor-desc.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-rifle.png"/> Гвинтівка**
  
  Вкладка `Гвинтівка` вміщує дані про `калібр`, `висоту прицілу`, `крок нарізів і їх напрямок` \
  <img src="pictures/editor-rifle.png"/>
  
* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-cartridge.png"/> Набій**

  У вкладці `Набій` можна змінити `початкову швидкість`, `тeмпературу` або `чутливість пороху`
  <img src="pictures/editor-cart.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-bullet.png"/> Куля, Драг-модуль і Балістичний коефіцієнт**

  У вкладці `Куля` можна відредагувати `фізичні параметри кулі`, змінити `тип драг-моделі`, вказати `спеціальну (кастомну) драг-функцію`, створити `таблицю мульти-БК`
  <img src="pictures/editor-bullet.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-zeroing.png"/> Пристрілка**

  Вкладка `Пристрілка` відображає:
  * поточну пристрілку профілю
  * атмосферу пристрілки

  Ці дані зберігаються у профіль автоматично під час пристрілки приладу  
  <img src="pictures/editor-zeroing.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-file-tree.png"/> Дистанції**
  
  Тут можна відредагувати `список робочих дистанцій` для поточного профілю
  <img src="pictures/editor-dist.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-file-tree.png"/> Дерево файлів**

  Вкладка `Дерево файлів` відображає:
  * перелік профілів у сховищі програми
  * підключені прилади і профілі в на їх флеш накопичувачах
  <img src="pictures/editor-file-tree.png"/>

## <span id="powder-sens"> Температурна залежність пороху </span>
Коефіцієнт температурної залежності `TC` пороху розраховується за формулами:
```
T2 і V2                          - нижча температура і нижча швидкість відповідно
ΔT = |T1 - T2|                   - різниця температур ºС
ΔV = |V1 - V2|                   - різниця початкових швидкостей для вказаних температур м/c
TC = ΔV / ΔT * (15 / Vl) * 100   - коефіцієнт температурної чутливості %/15ºС
```
**Важливо!** Для отримання точного коефіцієнту температурної чутливості пороху різниця температур має бути більше 10ºС

## <span id="faq"> Поширені запитання </span>

### <span id="app-run-issue"> Додаток не запусакється або не оновлюється </span>
* Windows:
  * Видаліть поточну інсталяцію додатку `Start` -> `Налаштування` -> `Програми та функцыъ` -> `ArcherBC2` -> `Видалити`
  * [Завантажте останнє оновлення тут](https://github.com/JAremko/ArcherBC2/releases/latest)
  * Встановіть завантажене оновлення