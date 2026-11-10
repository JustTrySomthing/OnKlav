# Code Edit

Included host app, available on Apps and Settings > Apps with the OnKlav code icon. Original native Android/Compose editor, inspired by desktop code-editor workflows; it does not embed Sublime Text or claim Sublime plugin compatibility. No new model, network service, permission or third-party dependency.

## Everyday workflow

Open a file through OnKlav Files or press + to create one with its extension. Up to eight tabs retain independent content, caret/selection and Undo/Redo history during the session. A dot means the file differs from its last explicit save. Drafts recover from private local storage; Save writes to Files. Closing changed tabs requires Save first, Keep editing or Discard.

The large native editor provides logical line numbers, native selection/copy/paste, horizontally scrolling long lines or optional wrapping, adjustable monospace text and theme-aware syntax colors. Highlighting runs off the main thread after a short debounce, applies color spans without resetting the selection, and bounds emitted spans to the visible neighborhood. It is lexical coloring, not a compiler/language server.

Rust, Kotlin, GDScript/Godot text resources, PHP, HTML, CSS, JavaScript, TypeScript, Go, Python, XML, JSON, YAML, TOML, Markdown, Shell, SQL, C/C++, Java, C# and Lua can be selected; extensions choose a default. Unknown text files open as Plain text. This covers source, template, configuration and theme files. Signed/compiled packages remain binary files and are not editable source.

The keyboard strip inserts indentation, outdents selected lines, wraps selections in brackets and offers common punctuation. Enter carries leading whitespace and adds indentation after opening delimiters/colon. The line/column footer jumps to a line. The options menu provides inline literal Find/Replace (case option, previous/next and confirmed Replace all), line comments where supported, Select all, language selection, open tabs, settings and strict background JSON validation. JSON validation does not use the permissive org.json parser.

When the IME opens, the large app heading and tab strip yield space to the editor. The current filename in the footer opens the tab chooser. Search remains inline and replaces the punctuation strip while active.

Hardware shortcuts: Ctrl+S Save, Ctrl+O Open, Ctrl+F Find, Ctrl+P tabs, Ctrl+Z Undo, Ctrl+Shift+Z/Ctrl+Y Redo; Tab/Shift+Tab indent/outdent.

## Saving and recovery

Strict UTF-8 and BOM-marked UTF-16LE/BE are supported; UTF BOM and consistent LF/CRLF/CR endings are retained on save. Mixed line endings normalize to LF, with a message and changed indicator before saving. Unsupported encodings/binary content are rejected. Files are bounded to 1 MB / 500,000 characters, open tabs to eight, history to 40 bounded snapshots per file; replace-all estimates size before allocation.

Save back is supported for selected internal OnKlav files. It verifies the original digest and name, creates a new signed Files content entry, retains the former version in Trash and uses a recovery journal for the name swap. External changes refuse replacement and preserve the draft for Save as. Read-only/connected-provider files use Save as through Files; external-provider overwrite is not advertised. New exported selections are promoted to a read selection for subsequent internal save-back. No direct content-index writes or arbitrary paths are accepted. Legacy protected-folder sources are refused to prevent private plaintext draft copies. Vault contents are not Files entries.

Private drafts use atomic writes, debounced persistence and a final snapshot on exit/background. Disk writes share one serial dispatcher. A failed draft recovery retains the old data and disables automatic replacement of it. Saved source files in Files follow the existing Files backup behavior; private working drafts are recovery data, not an advertised portable project format. Undo history is session-local; text, encoding, selected file and caret recover after process restart.

## Scope and verification

This is a local source editor, not an execution environment: no terminal, compiler, debugger, LSP or desktop plugin runtime. Physical-phone IME, accessibility, external-provider and large-file performance acceptance remain pending. Host verification and final compiled source hashes are recorded in `code-edit-verification.json`. No APK built for this task.

Final production and test compilation passed. All 14 focused checks passed in the final combined run: 9 text/encoding/lexer/JSON checks, 4 native Compose/editor checks and 1 actual signed Files replacement/conflict/Trash check. Source hashes match the compiled source. Initial fixture failure and final evidence are retained in `docs/code-edit-checks`. Native view renders are in `docs/evidence/code-edit`.
