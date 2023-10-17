# ArcherBC2 - USER GUIDE

## Intro
**The app is using to create and edit ballistic profiles for an \
**ARCHER THERMAL IMAGING SIGHTING SYSTEMS.**\
App uses special files format `.a7p` that supports by newest Archer devices.**

## Tabel of contents
* **[Start dialog](#start-dialog)**
* **[Settings](#app-settings)**
* **[Create ballistics profile](#creation-wizard)**
  * [Description](#wizard-desc)
  * [Rifle](#wizard-rifle)
  * [Cartridge](#wizard-cart)
  * [Bullet](#wizard-bullet)
  * [Distances set](#wizard-dist)
  * [Ballistic coefficient](#wizard-dm)
  * [Save dialog](#wizard-save)
* **[Ballistic profile editor](#profiles-editor)**
  * [Top-bar actions](#top-bar-actions)
  * [Sidebar tabs](#sidebar-tabs)
* **[Powder temperature sensitivity](#powder-sens)**
* **[FAQ](#faq)**
  * [App doesn't start or update not success](#app-run-issue)

## <span id="start-dialog"> Start dialog </span>

When starting the program, select the required option **"Create"** or **"Open"** and click **"Ok"**.
Depending on the choice, either **[Profile creation wizard](#creation-wizard)** or **file selection dialog** will open

<img alt="" src="pictures/start-dialog.png"/>

## <span id="app-settings"> Settings </span>

* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/icon-languages.png"/> - Language selection
<img alt="" align="bottom" src="pictures/language-selector.png"/>

* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/actions-group-theme.png"/> - Theme selection
<img alt="" align="bottom" src="pictures/theme-selector.png"/>

## <span id="creation-wizard"> Profile creation wizard </span>
### When you select the `Create` option, the `Profile Creation Wizard` starts
The steps to create an `.a7p` profile file are bellow

#### <span id="wizard-desc"> 1. Description </span>
Firstly, you need to fill in the `names of the profile, cartridge and bullet`, after click `Next`. This information will be displayed in the `Rifles` menu of the device.

![](pictures/wizard-desc.png)

#### <span id="wizard-rifle"> 2. Rifle parameters </span>

**Set the following parameters:**
* Barrel caliber - *can be entered manually or use the `Select` button*
* Twist
* Twist direction
* Sight height

![](pictures/wizard-rifle.png)

#### <span id="wizard-cartridge"> 3. Specify the cartridge parameters </span>

**Set the following parameters:**
* Powder temperature
* Base speed at the specified temperature
* [Coefficient of powder temperature sensitivity](#powder-sens)

![](pictures/wizard-cart.png)

#### <span id="wizard-bullet"> 4. Set the physical dimensions and weight of the bullet </span>

![](pictures/wizard-bullet.png)

#### <span id="wizard-dist"> 5. Choose a range of distances </span>

Choose the `range of working distances` according to your needs from the options offered
These distances will be available when selecting the current distance in the device interface

![](pictures/wizard-dist.png)

#### <span id="wizard-dm"> 6. Drag model and ballistic coefficient </span>
**In the following few points you need to choose:**
* Drag model `(G1 or G7)`
![](pictures/wizard-dm.png)

* Type of ballistic coefficient to use - `average BC (Single) or multi-BC`
![](pictures/wizard-bc-type.png)

* Depending on the selected BC type, enter:
  * Average BC
  ![](pictures/wizard-bc-single.png)
  * Or a multi-BC table for different speeds
  ![](pictures/wizard-bc-multi.png)

#### <span id="wizard-save"> 7. Save profile </span>
The program will offer to save the newly created profile to a file, choose a storage location and click `Save`.
At this stage, the profile is considered created and will be opened in the [editor](#profiles-editor), which is discussed in the next section
![](pictures/save-dialog.png)

## <span id="profiles-editor"> Ballistic profiles editor </span>
The editor opens immediately after creating a new profile, or when opening a profile from a `.a7p` file

### <span id="top-bar-actions"> Top-bar actions </span>
![](pictures/editor-top-bar.png)
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-new.png"/>
  Create - opens [profile creation wizard](#creation-wizard)
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-open.png"/>
  Open - opens a file selection dialog
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-save.png"/>
  Save - saves changes to the currently open file
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-save-as.png"/>
  Save as - opens a dialog for choosing a location to save the file
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-reload.png"/>
  Reload - restores data from the currently open file
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/load-zero-x-y.png"/>
  Import zeroing - allows you to download a zeroing from another file to the current one 
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-import.png"/>
  import ballistic profile from JSON
* <img alt="" align="center" src="../../../resources/skins/sol-dark/icons/file-export.png"/>
  export ballistic profile to JSON

### <span id="sidebar-tabs"> Sidebar tabs </span>
The sidebar tabs allow you to edit the ballistic parameters according to the sections
* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-description.png"/> Description**
  
  Here you can edit and change the `description of the ballistic profile`, this data will be displayed in the `Rifles` menu of the device \
  You can also change `the short names for the profile icon` (Options `Top` and `Bottom`)
  or add a note to the current profile \
  <img src="pictures/editor-desc.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-rifle.png"/> Rifle**
  
  The `Rifle` tab contains information about the `caliber`, `sight height`, `barrel twist` and `twist direction` \
  <img src="pictures/editor-rifle.png"/>
  
* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-cartridge.png"/> Cartridge**

  In the `Cartridge` tab, you can change the `muzzle velocity`, `temperature`, or `sensitivity of powder`
  <img src="pictures/editor-cart.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-bullet.png"/> Bullet, Drag-model and BC**

  In the `Bullet` tab, you can edit the `bullet's physical parameters`, change the `drag model type`, specify a `custom drag function`, create a `multi-BC table`
  <img src="pictures/editor-bullet.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-zeroing.png"/> Zeroing**

  
  The `Shooting` tab displays:
  * `current profile zeroing`
  * `zeroing atmosphere`

  These data are `saved in the profile automatically when aiming` the device 
  <img src="pictures/editor-zeroing.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-file-tree.png"/> Distances**

  Here you can edit the `working distances set` for the current profile
  <img src="pictures/editor-dist.png"/>

* **<img alt="" align="center" width=32 height=32 src="../../../resources/skins/sol-dark/icons/tab-icon-file-tree.png"/> Files tree**

  The File Tree tab displays:
  * a list of profiles in the program repository
  * connected devices and profiles in their flash drives  
  <img src="pictures/editor-file-tree.png"/>

## <span id="powder-sens"> Powder temperature sensitivity </span>
The coefficient of temperature dependence `TC' of powder is calculated according to the formulas below:
```
T2 і V2                           - lower temperature and lower speed respectively
ΔT = |T1 - T2|                    - temperature difference in ºС
ΔV = |V1 - V2|                    - difference in muzzle velocities for the indicated temperatures in m/c
TC = ΔV / ΔT * (15 / Vl) * 100    - powder temperature sensitivity coefficient in %/15ºС
```
**Important!** To obtain an accurate coefficient of temperature sensitivity of gunpowder, the temperature difference must be more than 10ºС

## <span id="faq"> FAQ </span>

### <span id="app-run-issue"> App doesn't start or update not success </span>
* Windows:
  * Delete the app instance through the `Start` -> `Settings` -> `Programs and features` -> `ArcherBC2` -> `Delete`
  * [Download the latest update here](https://github.com/JAremko/ArcherBC2/releases/latest)
  * Install downloaded package