# Godot4.7.2 single-thread group cleanup

Upstream wait_for_group_task_completion omitted all cleanup when THREADS_ENABLED was off. Tasks run synchronously in that configuration, leaving the waiter responsible for releasing and erasing the group. This patch adds that release; the threaded branch and physics algorithms are unchanged. The actual shared Web release runtime and developer export template are rebuilt together. See docs/godot-web-engine.json for upstream archive/checksum/build provenance.
