#  MessManager — Complete Technical & Architectural Documentation

> **Application Name:** MessManager  
> **Course:** CSE489 Mobile Application Development (Mini Project)  
> **Package Name:** `com.arman.messmanager`  
> **Developer:** Md Arman Hossain  
> **Repository Architecture:** Modern Android Clean Architecture + MVVM + Jetpack  
> **Database / Backend:** Firebase Authentication, Google Cloud Firestore, Firebase Storage  

---

##  Table of Contents

1. [Architectural Overview & Design Principles](#1-architectural-overview--design-principles)
2. [Complete Directory & File Structure](#2-complete-directory--file-structure)
3. [Cloud Firestore Database Schema](#3-cloud-firestore-database-schema)
4. [Data Layer: Models & Repositories](#4-data-layer-models--repositories)
5. [Presentation Layer: ViewModels & UI Fragments](#5-presentation-layer-viewmodels--ui-fragments)
6. [Core Business Logic & Step-by-Step Workflows](#6-core-business-logic--step-by-step-workflows)
   * [6.1 Auth & Session Lifecycle](#61-auth--session-lifecycle)
   * [6.2 Mess Creation & Join Flow](#62-mess-creation--join-flow)
   * [6.3 Super Admin Member Governance](#63-super-admin-member-governance)
   * [6.4 Dynamic Meal Rate & Live Balance Calculation](#64-dynamic-meal-rate--live-balance-calculation)
   * [6.5 Daily Meal Attendance & Lock Time Enforcement](#65-daily-meal-attendance--lock-time-enforcement)
   * [6.6 Finance Management (Bazaar, Fixed Bills, Deposits)](#66-finance-management-bazaar-fixed-bills-deposits)
   * [6.7 Special Meal Polling & Participant Cost Splitting](#67-special-meal-polling--participant-cost-splitting)
   * [6.8 Democratic Manager Election System](#68-democratic-manager-election-system)
   * [6.9 Close Month & Balance Rollover Engine](#69-close-month--balance-rollover-engine)
   * [6.10 Profile Management & Image Storage](#610-profile-management--image-storage)
   * [6.11 Digital Notice Board](#611-digital-notice-board)
7. [UI Design System, Navigation & Theming](#7-ui-design-system-navigation--theming)
8. [Build Configuration & Dependency Management](#8-build-configuration--dependency-management)
9. [Edge Cases, Error Handling & Security Analysis](#9-edge-cases-error-handling--security-analysis)

---

## 1. Architectural Overview & Design Principles

MessManager adheres to the **Android Architecture Guidelines** using a clean separation of concerns:

```text
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                       │
│  ┌───────────────────────┐       ┌────────────────────────┐ │
│  │  UI Fragments (XML)   │ <───> │ ViewModels (StateFlow) │ │
│  └───────────────────────┘       └────────────────────────┘ │
└──────────────────────────────┬──────────────────────────────┘
                               │ Calls Suspend Functions
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN / DATA LAYER                    │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ Repositories (Auth, User, Mess, MealEntry, Finance...) │ │
│  └───────────────────────────┬────────────────────────────┘ │
└──────────────────────────────┼──────────────────────────────┘
                               │ .await() via Play Services
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    REMOTE / CLOUD BACKEND                   │
│  ┌────────────────────┬─────────────────┬─────────────────┐ │
│  │ Firebase Auth      │ Cloud Firestore │ Firebase Storage│ │
│  └────────────────────┴─────────────────┴─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Core Design Principles:
1. **Single-Activity Architecture:** `MainActivity.kt` contains a single `FragmentContainerView` acting as the `NavHostFragment`. All screens are modular `Fragment` classes.
2. **Unidirectional Data Flow (UDF):** ViewModels expose state through immutable Kotlin `StateFlow<UiState>`. Fragments collect these flows within `repeatOnLifecycle(Lifecycle.State.STARTED)` to prevent background resource leaks.
3. **Structured Concurrency with Coroutines:** Asynchronous calls execute inside `viewModelScope.launch` with explicit timeouts (`withTimeout(15_000L)`). Firebase `Task` objects are bridged to suspending functions via `kotlinx.coroutines.tasks.await()`.
4. **Deterministic Document Identifiers:** Where applicable (e.g., `MealEntry`), composite deterministic document IDs prevent accidental duplicates in Firestore NoSQL collections.
5. **Local Persistent Cache:** Enabled in `MessManagerApp.kt` via `PersistentCacheSettings` to provide offline caching.

---

## 2. Complete Directory & File Structure

```text
MessManager/
├── app/
│   ├── build.gradle.kts                     # App-level build configuration & dependencies
│   ├── google-services.json                 # Firebase project configuration
│   └── src/main/
│       ├── AndroidManifest.xml              # Permissions & Application registration
│       ├── java/com/arman/messmanager/
│       │   ├── MainActivity.kt              # Single Activity host with Edge-to-Edge window insets
│       │   ├── MessManagerApp.kt            # Application class initializing Firestore cache
│       │   ├── data/
│       │   │   ├── model/                   # Pure Kotlin data classes (Data transfer models)
│       │   │   │   ├── BazaarEntry.kt       # Daily market expense entry model
│       │   │   │   ├── BazaarRoster.kt      # Duty roster model
│       │   │   │   ├── DailyBazaar.kt       # Daily bazaar model
│       │   │   │   ├── Deposit.kt           # Cash deposit model (pending/approved)
│       │   │   │   ├── ElectionPoll.kt      # Democratic election model
│       │   │   │   ├── Enums.kt             # UserRole, MealType, FixedBillType enums
│       │   │   │   ├── FixedBill.kt         # Shared fixed utility bill model
│       │   │   │   ├── InventoryItem.kt     # Shared grocery inventory model
│       │   │   │   ├── Meal.kt              # Meal count container model
│       │   │   │   ├── MealEntry.kt         # Individual daily meal log model
│       │   │   │   ├── Mess.kt              # Mess entity model (settings, menus, lock times)
│       │   │   │   ├── MonthSummary.kt      # Archived month snapshot & member slice
│       │   │   │   ├── Notice.kt            # Notice board announcement model
│       │   │   │   ├── SpecialMealPoll.kt   # Special feast opt-in poll model
│       │   │   │   └── User.kt              # User profile model
│       │   │   ├── remote/firebase/
│       │   │   │   └── FirestoreCollections.kt # Centralized collection name constants
│       │   │   └── repository/              # Data access layer interfacing with Firebase
│       │   │       ├── AuthRepository.kt
│       │   │       ├── UserRepository.kt
│       │   │       ├── MessRepository.kt
│       │   │       ├── MealEntryRepository.kt
│       │   │       ├── MealRepository.kt
│       │   │       ├── FinanceRepository.kt
│       │   │       ├── DepositRepository.kt
│       │   │       ├── FixedBillRepository.kt
│       │   │       ├── BazaarEntryRepository.kt
│       │   │       ├── BazaarRosterRepository.kt
│       │   │       ├── SpecialMealPollRepository.kt
│       │   │       ├── ElectionPollRepository.kt
│       │   │       ├── MonthSummaryRepository.kt
│       │   │       ├── NoticeRepository.kt
│       │   │       ├── InventoryRepository.kt
│       │   │       └── InventoryItemRepository.kt
│       │   └── ui/                          # Presentation Layer (MVVM)
│       │       ├── auth/                    # Login & Registration screens
│       │       │   ├── AuthViewModel.kt
│       │       │   ├── LoginFragment.kt
│       │       │   └── RegisterFragment.kt
│       │       ├── messsetup/               # Mess Creation & Join Screen
│       │       │   ├── MessSetupViewModel.kt
│       │       │   └── MessSetupFragment.kt
│       │       ├── pendingapproval/         # Waiting screen for unapproved members
│       │       │   ├── PendingApprovalViewModel.kt
│       │       │   └── PendingApprovalFragment.kt
│       │       ├── dashboard/               # Role-Specific Dashboards
│       │       │   ├── superadmin/          # Super Admin dashboard & settings
│       │       │   │   ├── SuperAdminDashboardViewModel.kt
│       │       │   │   ├── SuperAdminDashboardFragment.kt
│       │       │   │   └── MessSettingsFragment.kt
│       │       │   ├── financemanager/      # Finance Manager dashboard
│       │       │   │   ├── FinanceManagerDashboardViewModel.kt
│       │       │   │   └── FinanceManagerDashboardFragment.kt
│       │       │   ├── mealmanager/         # Meal Manager dashboard
│       │       │   │   ├── MealManagerDashboardViewModel.kt
│       │       │   │   └── MealManagerDashboardFragment.kt
│       │       │   └── member/              # General Member dashboard
│       │       │       ├── MemberDashboardViewModel.kt
│       │       │       └── MemberDashboardFragment.kt
│       │       ├── managemembers/           # Super Admin member approval/kick screen
│       │       │   ├── ManageMembersViewModel.kt
│       │       │   └── ManageMembersFragment.kt
│       │       ├── election/                # Manager Voting screen
│       │       │   ├── ElectionViewModel.kt
│       │       │   └── ElectionFragment.kt
│       │       ├── specialmealpoll/         # Special feast opt-in/opt-out screen
│       │       │   ├── SpecialMealPollViewModel.kt
│       │       │   └── SpecialMealPollFragment.kt
│       │       ├── profile/                 # Profile edit & photo upload screen
│       │       │   ├── ProfileViewModel.kt
│       │       │   └── ProfileFragment.kt
│       │       ├── inventory/               # Shared inventory & bazaar roster
│       │       │   ├── InventoryViewModel.kt
│       │       │   ├── InventoryFragment.kt
│       │       │   ├── InventoryAdapter.kt
│       │       │   └── BazaarRosterAdapter.kt
│       │       └── navigation/
│       │           └── RoleNavigation.kt    # Extension functions for role-based routing
│       └── res/                             # Layouts, Drawables, Navigation, Values
│           ├── layout/                      # All XML screen and row layouts
│           ├── navigation/nav_graph.xml     # Jetpack Navigation Graph
│           └── values/                      # colors.xml, strings.xml, plurals.xml, themes.xml
├── gradle/libs.versions.toml                # Gradle Version Catalog
├── SUMMARY.md                               # Executive summary & Viva cheat sheet
└── DETAILS.md                               # Full technical documentation (this file)
```

---

## 3. Cloud Firestore Database Schema

Firestore is organized into 12 root collections defined in `FirestoreCollections.kt`:

```text
Cloud Firestore
├── messes/                 (Mess configuration, lock times, daily menus)
├── users/                  (User profile, role, balance, mess association)
├── mealEntries/            (Individual meal toggle records: userId_date_mealType)
├── bazaarEntries/          (Daily market shopping expenses & receipts)
├── fixedBills/             (Shared monthly bills: rent, wifi, maid, etc.)
├── deposits/               (Cash deposit requests & approved records)
├── specialMealPolls/       (Feast events & participant user IDs list)
├── electionPolls/          (Manager election ballots & vote mappings)
├── monthSummaries/         (Immutable end-of-month financial audit snapshots)
├── notices/                (Broadcast announcements on notice board)
├── inventoryItems/         (Pantry stock items & low-stock flags)
└── bazaarRoster/           (Bazaar duty schedule)
```

### Detailed Document Schemas:

#### 1. `messes` Collection
* **Document ID:** Auto-generated UUID (e.g., `MESS_998877`)
```json
{
  "messId": "d742a8bc-...",
  "name": "Developers Hub Mess",
  "inviteCode": "M4V9ZQ",
  "superAdminUid": "arman_uid_77x",
  "currentMonthId": "2026-09",
  "dueThresholdBdt": 1000.0,
  "ramadanModeEnabled": false,
  "language": "en",
  "mealRate": 0.0,
  "breakfastLockTime": "08:30",
  "lunchLockTime": "12:00",
  "dinnerLockTime": "20:30",
  "breakfastMenu": "Ruti, Dal",
  "lunchMenu": "Rice, Beef, Vegetable",
  "dinnerMenu": "Rice, Egg Curry, Dal"
}
```

#### 2. `users` Collection
* **Document ID:** Firebase Authentication UID (`auth.currentUser.uid`)
```json
{
  "uid": "arman_uid_77x...",
  "name": "Md Arman Hossain",
  "email": "arman@example.com",
  "phone": "+8801700000000",
  "emergencyContact": "+8801800000000",
  "profilePictureUrl": "[https://firebasestorage.googleapis.com/.../profile.jpg](https://firebasestorage.googleapis.com/.../profile.jpg)",
  "messId": "d742a8bc-...",
  "role": "SUPER_ADMIN", // "SUPER_ADMIN" | "FINANCE_MANAGER" | "MEAL_MANAGER" | "MEMBER"
  "balance": 2150.00,    // Carried over opening balance from previous month
  "joinApproved": true
}
```

#### 3. `mealEntries` Collection
* **Document ID:** Composite Deterministic Key: `${userId}_${date}_${mealType}` (e.g., `arman_uid_77x_2026-09-15_LUNCH`)
```json
{
  "entryId": "arman_uid_77x_2026-09-15_LUNCH",
  "messId": "d742a8bc-...",
  "userId": "arman_uid_77x...",
  "date": "2026-09-15",
  "mealType": "LUNCH", // "BREAKFAST" | "LUNCH" | "DINNER" | "SEHRI" | "IFTAR"
  "count": 1.0,        // 1.0 if ON, 0.0 if OFF (or guest meal counts)
  "isGuestMeal": false,
  "overriddenByUid": null
}
```

#### 4. `bazaarEntries` Collection
* **Document ID:** Auto-generated UUID
```json
{
  "entryId": "baz_88412...",
  "messId": "d742a8bc-...",
  "date": "2026-09-15",
  "amount": 2200.00,
  "receiptPhotoUrl": null,
  "addedByUid": "finance_mgr_uid",
  "linkedPollId": null // null for standard daily meal fund; "poll_id" for special meals
}
```

#### 5. `fixedBills` Collection
* **Document ID:** Auto-generated UUID
```json
{
  "billId": "bill_66321...",
  "messId": "d742a8bc-...",
  "monthId": "2026-09",
  "type": "RENT", // "RENT" | "MAID" | "WIFI" | "ELECTRICITY" | "GAS" | "WATER" | "GARBAGE"
  "amount": 15000.00,
  "date": "2026-09-01T00:00:00Z",
  "addedBy": "finance_mgr_uid"
}
```

#### 6. `deposits` Collection
* **Document ID:** Auto-generated UUID
```json
{
  "depositId": "dep_22345...",
  "messId": "d742a8bc-...",
  "memberUid": "arman_uid_77x",
  "amount": 5000.00,
  "date": "2026-09-05T14:22:00Z",
  "status": "approved" // "pending" | "approved"
}
```

#### 7. `specialMealPolls` Collection
* **Document ID:** Auto-generated UUID
```json
{
  "pollId": "poll_tehari_88",
  "messId": "d742a8bc-...",
  "title": "Friday Beef Tehari Feast",
  "eventDate": "2026-09-20",
  "optedInUserIds": ["uid_1", "uid_2", "uid_3"],
  "costPerHead": null,
  "closed": false
}
```

#### 8. `electionPolls` Collection
* **Document ID:** Auto-generated UUID
```json
{
  "pollId": "elect_2026_10",
  "messId": "d742a8bc-...",
  "title": "Manager Election for 2026-10",
  "options": ["uid_member_1", "uid_member_2", "uid_member_3"],
  "financeVotes": {
    "voter_uid_1": "uid_member_2",
    "voter_uid_2": "uid_member_2"
  },
  "mealVotes": {
    "voter_uid_1": "uid_member_3"
  },
  "status": "open", // "open" | "closed"
  "monthId": "2026-10",
  "startTime": 1757872000000,
  "endTime": 1757958400000,
  "roles": ["finance", "meal"],
  "winners": {}
}
```

#### 9. `monthSummaries` Collection
* **Document ID:** Auto-generated UUID
```json
{
  "summaryId": "summary_2026_09",
  "messId": "d742a8bc-...",
  "monthId": "2026-09",
  "totalStandardBazaarCost": 38500.00,
  "totalSpecialMealCost": 5200.00,
  "totalFixedBillsCost": 24000.00,
  "totalExpenses": 67700.00,
  "totalDeposits": 70000.00,
  "totalStandardMeals": 610.0,
  "mealRate": 63.11,
  "memberSummaries": [
    {
      "uid": "arman_uid_77x",
      "name": "Md Arman Hossain",
      "openingBalance": 1500.00,
      "totalDeposits": 5000.00,
      "totalMeals": 60.0,
      "mealCost": 3786.60,
      "fixedBillShare": 2400.00,
      "specialMealCost": 850.00,
      "closingBalance": -536.60
    }
  ],
  "newFinanceManagerUid": null,
  "newMealManagerUid": null,
  "closedByUid": "finance_mgr_uid",
  "closedAtEpochMs": 1759247999000
}
```

#### 10. `notices` Collection
* **Document ID:** Auto-generated UUID
```json
{
  "noticeId": "not_6621",
  "messId": "d742a8bc-...",
  "title": "Electricity Maintenance Tomorrow",
  "content": "Electricity will be off from 10:00 AM to 2:00 PM for maintenance.",
  "postedBy": "super_admin_uid",
  "date": 1757874000000
}
```

---

## 4. Data Layer: Models & Repositories

### Repositories Explained:

1. **`AuthRepository.kt`:**
   * Encapsulates `FirebaseAuth.getInstance()`.
   * Methods: `signIn(email, password)`, `register(email, password)`, `signOut()`, `currentUser`.
2. **`UserRepository.kt`:**
   * Manages user profiles in Firestore.
   * `getUser(uid)`: Fetches a single user document and maps it to `User.kt`.
   * `createUser(user)`: Writes or updates user profile.
   * `getUsersForMess(messId)`: Fetches all members attached to a mess using `.whereEqualTo("messId", messId)`.
   * `removeMember(uid)`: Clears user's `messId` to disassociate them from the mess.
   * `setRole(uid, role)`: Updates user role.
   * `removeAllMembersFromMess(messId)`: Batch update used when a mess is deleted.
3. **`MessRepository.kt`:**
   * `createMess(name, superAdminUid)`: Generates a 6-character uppercase alphanumeric invite code (excluding confusing characters `I, O, 0, 1`) and creates the mess document.
   * `findByInviteCode(inviteCode)`: Searches for a mess by its unique invite code.
   * `setMealLockTime(messId, mealType, time)`: Updates lock times (`breakfastLockTime`, `lunchLockTime`, `dinnerLockTime`).
   * `advanceToMonth(messId, newMonthId)`: Advances `currentMonthId` during month-closing.
   * `deleteMess(messId)`: Deletes the mess document.
4. **`MealEntryRepository.kt`:**
   * `buildEntryId(userId, date, mealType)`: Builds `${userId}_${date}_${mealType.name}`.
   * `getMealsForDate(userId, date)`: Retrieves user's meals for a single day.
   * `getMealsForMessAndDate(messId, date)`: Retrieves all mess members' entries for a specific day to compute today's summary.
   * `getMealsForMess(messId)`: Retrieves all meal logs for the mess to compute monthly totals.
   * `setMealOn(messId, userId, date, mealType, isOn)`: Writes `count = 1.0` if `isOn` else `0.0`.
5. **`DepositRepository.kt`:**
   * `addDeposit(messId, memberUid, amount, status)`: Creates a deposit record (`pending` when requested by member, `approved` when entered directly by manager).
   * `approveDeposit(depositId)`: Updates status to `"approved"`.
   * `rejectDeposit(depositId)`: Deletes the pending deposit document.
6. **`FixedBillRepository.kt`:**
   * `getFixedBills(messId, monthId)`: Queries bills scoped to a specific month.
   * `addFixedBill(...)` / `updateFixedBill(...)`: Upserts utility and rent expenses.
7. **`BazaarEntryRepository.kt`:**
   * `addBazaarEntry(messId, amount, addedByUid, linkedPollId)`: Adds daily grocery expense. If `linkedPollId` is set, it links exclusively to a special feast.
8. **`SpecialMealPollRepository.kt`:**
   * `createPoll(messId, title, eventDate)`: Creates a new feast poll.
   * `optIn(pollId, uid)`: Adds member UID using `FieldValue.arrayUnion(uid)`.
   * `optOut(pollId, uid)`: Removes member UID using `FieldValue.arrayRemove(uid)`.
9. **`ElectionPollRepository.kt`:**
   * `createPoll(...)`: Creates election with eligible member UIDs as candidate options and computes `endTime`.
   * `castVote(pollId, voterUid, role, candidateUid)`: Atomically updates `financeVotes.<voterUid>` or `mealVotes.<voterUid>` using dot-notation.
   * `closePoll(pollId)`: Sets status to `"closed"`.
10. **`MonthSummaryRepository.kt`:**
    * `archiveMonth(summary)`: Saves immutable financial snapshot at month-end.

---

## 5. Presentation Layer: ViewModels & UI Fragments

### Screen-by-Screen Breakdown:

#### 1. Authentication (`LoginFragment.kt` & `RegisterFragment.kt`)
* **ViewModel:** `AuthViewModel.kt`
* **States:** `Idle`, `Loading`, `LoginSuccess(role)`, `RegisterSuccess`, `NeedsMessSetup`, `PendingApproval`, `Error(msg)`
* **Auto-Login:** `checkCurrentUserSession()` verifies current token on app startup, checks if `user.messId` exists and whether `joinApproved == true`, then dispatches directly to the user's role dashboard without showing login prompts.

#### 2. Mess Setup (`MessSetupFragment.kt`)
* **ViewModel:** `MessSetupViewModel.kt`
* **Actions:**
  * **Create Mess:** Prompts for mess name, invokes `MessRepository.createMess()`, promotes creator to `SUPER_ADMIN` with `joinApproved = true`, displays generated invite code dialog, and opens Super Admin Dashboard.
  * **Join Mess:** Takes 6-digit invite code, looks up mess ID, updates user profile with `role = MEMBER` and `joinApproved = false`, and routes to `PendingApprovalFragment`.

#### 3. Pending Approval (`PendingApprovalFragment.kt`)
* **ViewModel:** `PendingApprovalViewModel.kt`
* **Functionality:** Displays waiting status. Features a "Check Approval Status" button that re-fetches the user profile. When `isApproved == true`, immediately navigates to `navigateToRoleDashboard(role)`.

#### 4. Super Admin Dashboard (`SuperAdminDashboardFragment.kt`)
* **ViewModel:** `SuperAdminDashboardViewModel.kt`
* **Key Features:**
  * Displays Total Members count, Active Managers count, Personal Balance, Personal Meals Today.
  * **Trigger Manager Elections:** Displays dialog with checkboxes for Finance Manager and Meal Manager roles, and election duration in hours.
  * **Assign Roles:** Displays member list and assigns `FINANCE_MANAGER` or `MEAL_MANAGER` roles.
  * **Remove Member:** Kicks a member from the mess.
  * **Mess Settings Navigation:** Opens `MessSettingsFragment.kt` to edit mess name, threshold due limit, and toggle Ramadan mode.
  * **Delete Mess:** Emergency action that disassociates all members and removes the mess document.

#### 5. Finance Manager Dashboard (`FinanceManagerDashboardFragment.kt`)
* **ViewModel:** `FinanceManagerDashboardViewModel.kt`
* **Key Features:**
  * **Pending Approvals Card:** Shows count of pending member deposit requests. Clicking opens a dialog allowing one-tap **APPROVE** or **REJECT**.
  * **Mess Balance Card:** Shows net mess fund (`Total Deposits - Total Expenses`). Clicking shows live balances for every member.
  * **Total Expenses Card:** Shows total spending (`Bazaar + Fixed Bills`). Clicking shows full breakdown.
  * **Add Daily Bazaar:** Input amount with optional dropdown to link expense to an active Special Meal Poll.
  * **Manage Fixed Costs:** Manage Rent, Maid, WiFi, Garbage, Water bills.
  * **Add Regular Utility Bills:** Add Electricity and Gas bills.
  * **Log Cash Deposit:** Manually record deposits received directly in cash.
  * **Close Month:** Triggers complete month-closing calculation and balance carryover.

#### 6. Meal Manager Dashboard (`MealManagerDashboardFragment.kt`)
* **ViewModel:** `MealManagerDashboardViewModel.kt`
* **Key Features:**
  * **Today's Meal Summary Cards:** Shows universal meal count for Breakfast, Lunch, and Dinner (adapted to Sehri/Iftar in Ramadan mode).
  * **Meal Attendance Inspection:** Clicking any meal card reveals the exact list of members eating that meal today, with a direct button to **Set Menu**.
  * **Lock Meals Cut-off Time:** Opens `TimePickerDialog` to set cut-off time for Breakfast, Lunch, or Dinner.
  * **Create Special Meal Poll:** Opens dialog + `DatePickerDialog` to schedule a special feast.
  * **Manage Special Meals:** View who is IN/OUT for each poll, with ability to manually add members.

#### 7. General Member Dashboard (`MemberDashboardFragment.kt`)
* **ViewModel:** `MemberDashboardViewModel.kt`
* **Key Features:**
  * Displays Personal Balance, Meals Consumed Today, and Live Dynamic Meal Rate.
  * **Daily Meal Switches:** Three Material Switches (Breakfast, Lunch, Dinner) that can be turned ON/OFF before the manager's lock time.
  * **Lock Enforcement:** If a member tries turning OFF a locked meal, an alert toast warns: *"Cannot turn off Lunch. Locked since 12:30"*.
  * **Daily Menu Cards:** Shows menus set by the Meal Manager.
  * **Deposit Request:** Input amount to notify the Finance Manager.
  * **Active Banners:** Dynamic banners for active manager elections and open special meal polls.
  * **Digital Notice Board:** Displays latest broadcast announcements with timestamps and author names.

#### 8. Manager Election Screen (`ElectionFragment.kt`)
* **ViewModel:** `ElectionViewModel.kt`
* **Key Features:**
  * Displays election title, countdown/end time, and candidate cards for Finance Manager and Meal Manager.
  * Shows live vote tallies (`X votes`) next to each candidate.
  * Clicking a candidate casts/updates the user's vote instantly and highlights the card in accent blue.

#### 9. Special Meal Poll Screen (`SpecialMealPollFragment.kt`)
* **ViewModel:** `SpecialMealPollViewModel.kt`
* **Key Features:**
  * Lists all open feast polls with event date and total opted-in count.
  * Provides independent **YES (I'm In)** and **NO (I'm Out)** buttons with active state highlighting.

#### 10. Profile Screen (`ProfileFragment.kt`)
* **ViewModel:** `ProfileViewModel.kt`
* **Key Features:**
  * Updates Name and Phone number.
  * Profile picture upload: Uses `ActivityResultContracts.GetContent()` to pick an image from device gallery, uploads it to `FirebaseStorage` under `profile_pictures/{uid}.jpg`, retrieves the public download URL, and updates the Firestore user profile.

---

## 6. Core Business Logic & Step-by-Step Workflows

### 6.1 Auth & Session Lifecycle
1. User enters Email and Password in `LoginFragment.kt`.
2. `AuthViewModel.kt` validates email format via `Patterns.EMAIL_ADDRESS` and password length (>= 6).
3. Calls `AuthRepository.signIn(email, password)` within a 10-second coroutine timeout.
4. Reads Firestore user profile `UserRepository.getUser(uid)`:
   * `user == null` -> User profile missing.
   * `user.messId == null` -> Emits `NeedsMessSetup` -> Navigates to `MessSetupFragment`.
   * `!user.joinApproved` -> Emits `PendingApproval` -> Navigates to `PendingApprovalFragment`.
   * `user.joinApproved == true` -> Emits `LoginSuccess(user.role)` -> Navigates to the corresponding dashboard.

### 6.2 Mess Creation & Join Flow
* **Creation:**
  1. User enters Mess Name.
  2. `MessRepository.createMess()` generates a 6-character random uppercase code (`(1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }`) and sets `currentMonthId = YearMonth.now().toString()`.
  3. User's profile is updated with `messId`, `role = SUPER_ADMIN`, `joinApproved = true`.
  4. Invite code is shown in an uncancelable dialog for the creator to copy.
* **Joining:**
  1. User enters invite code.
  2. Code is normalized (`uppercase().trim()`) and looked up in Firestore.
  3. User profile is updated with `messId = mess.messId`, `role = MEMBER`, `joinApproved = false`.
  4. Navigates to `PendingApprovalFragment`.

### 6.3 Super Admin Member Governance
* **Approval:** Super Admin views pending users in `ManageMembersFragment.kt`. Clicking "Approve" sets `joinApproved = true`.
* **Role Assignment:** Super Admin selects a member and chooses `FINANCE_MANAGER` or `MEAL_MANAGER`. `UserRepository.setRole()` updates the document in Firestore.
* **Removal:** Super Admin clicks "Remove". `UserRepository.removeMember()` sets `messId = null`, `joinApproved = false`, and resets `role = MEMBER`.

### 6.4 Dynamic Meal Rate & Live Balance Calculation
Every dashboard computes live financial values dynamically using the following algorithms:

```kotlin
// 1. DYNAMIC MEAL RATE
val monthlyStandardBazaarCost = bazaarEntries
    .filter { it.date.startsWith(monthId) && it.linkedPollId == null }
    .sumOf { it.amount }

val totalMessMeals = mealEntriesAll.sumOf { it.count }
val mealRate = if (totalMessMeals > 0.0) monthlyStandardBazaarCost / totalMessMeals else 0.0

// 2. PERSONAL MEAL COST
val myMealsCount = mealEntriesAll.filter { it.userId == uid }.sumOf { it.count }
val myMealCost = myMealsCount * mealRate

// 3. FIXED BILL SHARE
val monthlyFixedBillsCost = fixedBills.sumOf { it.amount }
val fixedBillShare = if (approvedMembers.isNotEmpty()) monthlyFixedBillsCost / approvedMembers.size else 0.0

// 4. SPECIAL MEAL EXPENSE SHARE
val specialCost = linkedBazaarEntries.sumOf { entry ->
    val participants = participantsByPollId[entry.linkedPollId].orEmpty()
    if (uid in participants) entry.amount / participants.size.coerceAtLeast(1) else 0.0
}

// 5. APPROVED DEPOSITS
val myApprovedDeposits = deposits
    .filter { it.status == "approved" && it.memberUid == uid && format(it.date) == monthId }
    .sumOf { it.amount }

// 6. NET LIVE BALANCE
val personalBalance = (user.balance) + myApprovedDeposits - (myMealCost + fixedBillShare + specialCost)
```

### 6.5 Daily Meal Attendance & Lock Time Enforcement
1. **Default State:** By default, every approved member is counted as eating (1.0) unless they explicitly turn OFF their meal toggle in `mealEntries`.
2. **Lock Cut-off Check:**
   * When a member turns OFF a switch on `MemberDashboardFragment`, `MemberDashboardViewModel.toggleMeal()` checks `LocalTime.now()` against `mess.lunchLockTime` (e.g. `12:30`).
   * If `LocalTime.now().isAfter(lockTime)`, the ViewModel rejects the action, reloads the previous state, and emits `ToggleError.TimeLocked`.
   * If before the cut-off, it writes `count = 0.0` to Firestore document `${uid}_${today}_${mealType}`.

### 6.6 Finance Management (Bazaar, Fixed Bills, Deposits)
* **Bazaar Entries:** Logged with amount and optional `linkedPollId`.
* **Fixed Bills:** Upserted for each `FixedBillType` (Rent, Maid, Wifi, etc.) for the active `monthId`.
* **Deposits:**
  * Member clicks "Send Deposit Request" on dashboard -> saved with `status = "pending"`.
  * Finance Manager sees pending count -> taps card -> reviews list -> taps **APPROVE** (`update("status", "approved")`) or **REJECT** (`delete()`).

### 6.7 Special Meal Polling & Participant Cost Splitting
1. Meal Manager creates poll: *"Friday Beef Tehari Feast"* on `2026-09-20`.
2. Members vote **YES** (adds UID to `optedInUserIds`) or **NO** (removes UID from `optedInUserIds`).
3. When buying ingredients, Finance Manager logs a Bazaar entry with `linkedPollId = "poll_tehari_88"`.
4. This expense is excluded from the standard daily meal rate.
5. During month closing, the total cost of this bazaar entry is divided equally **only among the UIDs present in `optedInUserIds`**.

### 6.8 Democratic Manager Election System
1. Super Admin triggers election for month `2026-10`, setting duration (e.g. 24 hours) and roles to elect (`finance`, `meal`).
2. All approved member UIDs are populated as options in `electionPolls`.
3. Members open `ElectionFragment` and tap their chosen candidate.
4. Firestore updates the map: `"financeVotes.<voterUid>": "<candidateUid>"`.
5. Vote tallies are calculated live via `.values.groupingBy { it }.eachCount()`.

### 6.9 Close Month & Balance Rollover Engine
When the Finance Manager taps **"Close Month Now"**, `FinanceManagerDashboardViewModel.kt` executes `runClose()`:

```text
[1. Fetch All Active Records for Current Month]
    ├── Approved Deposits
    ├── Standard Bazaar & Special Linked Bazaar Entries
    ├── Fixed Utility Bills
    └── All Member Meal Entries
           │
[2. Perform Final Audit Calculations]
    ├── Total Standard Meals = sum(mealEntries.count)
    ├── Final Meal Rate = StandardBazaar / TotalStandardMeals
    ├── Fixed Bill Per Head = TotalFixedBills / MemberCount
    └── For Each Member:
           Closing Balance = Opening Balance + Deposits - (Meals*Rate + FixedShare + SpecialShare)
           │
[3. Archive to Firestore]
    └── Save MonthSummary document (immutable record with full member breakdown list)
           │
[4. Carry Over Balances]
    └── Update each User document: user.balance = closingBalance
           │
[5. Rotate Month & Finalize]
    ├── Close active election polls
    └── Advance mess.currentMonthId -> next month (e.g. "2026-08" -> "2026-09")
```

---

## 7. UI Design System, Navigation & Theming

### Color Palette (`colors.xml`):
* `brand_primary`: `#2C3E50` (Deep Navy Charcoal — Headers, Primary Text, Cards)
* `brand_accent`: `#2980B9` (Vibrant Blue — Buttons, Active Toggles, Selection Highlights)
* `text_secondary`: `#7F8C8D` (Muted Slate Grey — Subtitles, Meta Labels)
* `gradient_start`: `#EAF2F8` / `gradient_end`: `#FFFFFF` (Subtle Soft Background Gradient)
* `error_red`: `#C0392B` (Warnings, Negative Balances, Rejections)
* `holo_green_dark`: `#27AE60` (Positive Balances, Approvals, Status Badges)

### Typography & Controls:
* Material Components (MDC): `com.google.android.material.card.MaterialCardView` with rounded corners (12dp-16dp) and subtle elevation (2dp-4dp).
* Inputs: `TextInputLayout` with floating hint labels and built-in error states.
* Toggles: `com.google.android.material.switchmaterial.SwitchMaterial`.

---

## 8. Build Configuration & Dependency Management

### App Gradle Configuration (`app/build.gradle.kts`):
* `compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`
* `viewBinding = true`
* JVM Compatibility: `JavaVersion.VERSION_11`

### Version Catalog (`gradle/libs.versions.toml`):
* **Android Gradle Plugin:** `8.5.1`
* **Kotlin:** `1.9.24`
* **Firebase BOM:** `33.2.0`
* **Lifecycle KTX:** `2.7.0`
* **Navigation Component:** `2.7.7`
* **Kotlinx Coroutines:** `1.8.1`
* **Glide:** `4.16.0`
* **Material Components:** `1.10.0`

---

## 9. Edge Cases, Error Handling & Security Analysis

1. **Composite Index Avoidance:**
   * Compound queries with multiple inequality filters require manual Firestore index creation.
   * To prevent crashes, all repositories perform single equality queries (e.g. `whereEqualTo("messId", messId)`) and execute complex filtering/sorting in-memory on the client.
2. **Network Timeouts & Fail-Safe Defaults:**
   * Network operations use `withTimeout(10_000L)` / `withTimeout(15_000L)` to prevent endless loading spinners on weak mobile data connections.
3. **Deterministic ID Idempotency:**
   * Using deterministic IDs `${userId}_${date}_${mealType}` ensures rapid clicking of meal switches updates the existing document rather than spamming duplicate records.
4. **Division-by-Zero Guards:**
   * All balance and rate formulas explicitly check: `if (totalMeals > 0) cost / totalMeals else 0.0` and `if (members.isNotEmpty()) cost / members.size else 0.0`.
5. **Memory Leak Prevention:**
   * ViewBinding properties in Fragments are set to `null` in `onDestroyView()`.
   * Flows are collected only while the Fragment lifecycle is at least `STARTED`.