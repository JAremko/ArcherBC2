# Введення
**Додаток використовується для створення і редагуванння балістичних профілів для\
**Тепловізійних стрілецьких прицільних комплексів ARCHER.**\
Додаток використовує спеціально розроблений формат файлів `.a7p` який підтримується найновішими приладами Archer.**

## Зміст
* **[Діалог запуску](#start-dialog)**
* **[Налаштування](#app-settings)**
* **[Створення балістичного профілю](#creation-wizard)**
* **[Редагування профілю](#profiles-editor)**
  * [Верхня панель](#top-bar-actions)
  * [Вкладки бокової панелі](#side-bar-tabs)
* **[Дерево файлів](#device-files-tree)**
* **[Використання Мульти-БК](#multibc-usage)**

## <span id="start-dialog"> Діалог запуску </span>

Під час запуску програми виберіть необхідний варіант із випадаючого списку **«Створити»** або **«Відкрити»** і натисніть **«Ок»**.
В залежності від вибору відкриється або **[Майстер створення профілю](#creation-wizard)** або **діалог вибору файлу** 

<img alt="None" src="pictures/start-dialog-ua.png"/>
<img alt="None" src="pictures/start-dialog1-ua.png"/>

## <span id="app-settings"> Налаштування </span>

* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/icon-languages.png"/> - Вибір мови
![](pictures/language-selector.png)
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/actions-group-theme.png"/> - Вибір теми
![](pictures/theme-selector.png)
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
* Коефіцієнт температурної чутливості пороху

![](pictures/wizard-cart.png)

#### <span id="wizard-bullet"> 4. Вкажіть фізичні розміри і вагу кулі </span>

![](pictures/wizard-bullet.png)

#### <span id="wizard-dist"> 5. Виберіть діапазон дистанцій </span>

Оберіть діапазон робочих дистанцій відповідно до ваших потреб із запропонованих варіантів
Ці дистанції будуть доступні у при виборі поточної дистанції в інтерфейсі приладу

![](pictures/wizard-dist.png)

#### <span id="wizard-dist"> 6. Драг-модель і балістичний коефіцієнт </span>
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

#### <span id="wizard-dist"> 7. Збереження профілю </span>
Програма запропонує зберегти щойно створений профіль до файлу, оберіть місце збереження і натисніть `Save`.
На цьому етапі профіль вважається створеним і буде відкритий в [редакторі](#profiles-editor), мова про який йде в наступному розділі

## <span id="profiles-editor"> Profiles editor </span>

...........

### <span id="top-bar-actions"> Top-bar actions </span>

* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/file-new.png"/> Create new
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/file-open.png"/> Open 
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/file-save.png"/> Save 
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/file-save-as.png"/> Save as 
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/file-reload.png"/> Reload 
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/load-zero-x-y.png"/> Import zeroing 
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/file-import.png"/> Import to JSON 
* <img alt="None" align="center" src="../resources/skins/sol-dark/icons/file-export.png"/> Export to JSON

### <span id="top-bar-actions"> Sidebar tabs </span>

* <img alt="None" align="center" width=32 height=32 src="../resources/skins/sol-dark/icons/tab-icon-description.png"/> Profile description
* <img alt="None" align="center" width=32 height=32 src="../resources/skins/sol-dark/icons/tab-icon-rifle.png"/> Rifle properties
* <img alt="None" align="center" width=32 height=32 src="../resources/skins/sol-dark/icons/tab-icon-cartridge.png"/> Cartridge data
* <img alt="None" align="center" width=32 height=32 src="../resources/skins/sol-dark/icons/tab-icon-bullet.png"/> Bullet data and Drag Model
* <img alt="None" align="center" width=32 height=32 src="../resources/skins/sol-dark/icons/tab-icon-zeroing.png"/> Zeroing
* <img alt="None" align="center" width=32 height=32 src="../resources/skins/sol-dark/icons/tab-icon-file-tree.png"/> File tree

## <span id="device-files-tree"> Device files tree </span>
...........

## <span id="multibc-usage"> MultiBC usage </span>
...........
