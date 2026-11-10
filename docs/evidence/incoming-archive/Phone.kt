package app.onklav.home
fun phoneKey(s: String) =
    s.filter { it.isDigit() }.let { if (it.length == 11 && it.startsWith("1")) it.drop(1) else it }
