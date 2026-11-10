package app.onklav.home

import javax.mail.internet.InternetAddress

/** Group by explicit Message-ID links only; never merge unrelated equal subjects or accounts. */
internal fun mailMessageIds(raw:String)=Regex("<[^<>\\s]{1,990}>").findAll(raw.take(24000)).map{it.value}.filter{it.contains('@')&&it.none(Char::isISOControl)}.take(20).toList()
internal fun mailConversations(rows:List<MailHeader>):List<List<MailHeader>>{
 val parent=IntArray(rows.size){it};fun root(n:Int):Int{var p=n;while(parent[p]!=p){parent[p]=parent[parent[p]];p=parent[p]};return p}
 val owner=HashMap<String,Int>()
 rows.forEachIndexed{i,h->(mailMessageIds(h.messageId)+h.references).distinct().forEach{id->val key=h.account+"\u0000"+id;val prior=owner.putIfAbsent(key,i);if(prior!=null){val a=root(i);val b=root(prior);if(a!=b)parent[a]=b}}}
 return rows.indices.groupBy{root(it)}.values.map{ids->ids.map(rows::get).sortedByDescending{it.date}}.sortedByDescending{it.first().date}
}
internal data class MailRecipient(val email:String,val name:String){val formatted:String get()=if(name.isBlank())email else InternetAddress(email,name,"UTF-8").toUnicodeString()}
internal fun mailRecipientStart(raw:String):Int{var quote=false;var escape=false;var angle=0;var start=0;raw.forEachIndexed{i,c->if(escape)escape=false else when(c){'\\'->if(quote)escape=true;'"'->quote=!quote;'<'->if(!quote)angle++;'>'->if(!quote)angle=(angle-1).coerceAtLeast(0);',',';'->if(!quote&&angle==0)start=i+1}};return start}
internal fun mailRecipientSuggestions(raw:String,account:String,headers:List<MailHeader>,drafts:List<MailDraft>):List<MailRecipient>{
 val start=mailRecipientStart(raw);val term=raw.substring(start).trim().lowercase();if(term.length !in 2..100)return emptyList()
 val used=runCatching{MailMime.addresses(raw.substring(0,start).trim().trimEnd(',',';')).map{it.address.lowercase()}.toSet()}.getOrDefault(emptySet())
 val seen=HashSet<String>();val result=ArrayList<MailRecipient>()
 val fields=sequence{headers.asSequence().filter{it.account==account}.sortedByDescending{it.date}.take(1500).forEach{yield(it.from);yield(it.to);yield(it.cc);yield(it.replyTo)};drafts.asSequence().filter{it.account==account}.sortedByDescending{it.modified}.forEach{yield(it.to);yield(it.cc);yield(it.bcc)}}
 for(field in fields)for(a in runCatching{MailMime.addresses(field).toList()}.getOrDefault(emptyList())){val email=a.address.lowercase();val name=a.personal.orEmpty();if(email !in used&&seen.add(email)&&(email.contains(term)||name.contains(term,true))){result.add(MailRecipient(a.address,name));if(result.size==5)return result}}
 return result
}
internal fun mailInsertRecipient(raw:String,recipient:MailRecipient)=raw.substring(0,mailRecipientStart(raw))+recipient.formatted+", "
/** Retain older downloaded pages while refreshing a head window, but discard stale UIDVALIDITY data. */
internal fun mailMergePage(old:List<MailHeader>,page:List<MailHeader>,folder:String,validity:Long,older:Boolean=false):List<MailHeader>{
 val lowest=page.minOfOrNull{it.uid}?:Long.MIN_VALUE
 return (page+old.filter{!mailSameFolder(it.folder,folder)||it.validity==validity&&(older||it.uid<lowest)}).distinctBy{it.key}.sortedByDescending{it.date}.take(5000)
}
