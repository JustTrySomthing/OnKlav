# Apps and Read Aloud polish — 0.90.0/code98

Apps has theme-tinted, subtle previous/next chevrons at the bottom corners beside the page indicators. Buttons use 48dp touch targets, accessible labels, animated paging and disabled end states; existing swipe and icon-rearrangement gestures remain available.

Notes and Annotation expose their existing saved-item drawers through a folder icon, with New first in each list. Export and settings remain directly accessible. The redundant host overflow is removed from these apps and Read Aloud; the existing top-left Back exits the internal app.

Read Aloud uses previous/play-pause/next icons. Clear and Paste align right above the scrub bar, with progress at left. Ready and the repeated text preview are removed. The saved text editor uses the gained space, highlights the current spoken word using the same primary color/bold treatment as the eBook reader, follows the speech range through long text, and displays a subtle scrollbar only when text overflows. Editing, seek, paste, clear confirmation and voice/speed settings remain available. Speech range support depends on the installed Android speech engine, as with the eBook reader.

Convert's transparent, theme-tinted outline vector now depicts a ruler inside two curved conversion arrows, based on the user's reference artwork without adopting its colors.

Only the OnKlav host is installable. Included apps retain the existing publisher and Android identity. No APK was requested for this source update. Core source/JNI is unchanged.

Validation results are recorded in apps-reader-polish-verification.json and evidence/v0.90.0. Actual phone touch/speech acceptance is separate.

Completed validation:134 controller checks,12 unique selected Android cases across focused runs, and lint with0errors/182warnings. Paging and word-highlight/scroll screenshots were visually inspected locally; screenshots are internal QA, not a source deliverable. All16signed included apps and the native reuse plan are current. No new APK assembled.
