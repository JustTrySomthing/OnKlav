package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Eight deliberately assigned numbers; never substitute a newly edited contact number. */
@Composable internal fun PhoneSpeedDialDialog(m:PhoneModel,assign:Int?,select:(Int?)->Unit,close:()->Unit,onCall:(String)->Unit){
 var query by remember(assign){mutableStateOf("")};var options by remember{mutableStateOf<Int?>(null)}
 val colors=MaterialTheme.colorScheme
 AlertDialog(onDismissRequest={if(assign!=null)select(null)else close()},title={Text(if(assign==null)"Speed dial"else"Assign speed dial $assign")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  if(assign==null){
   Text("Tap to call, or hold 2–9 on the keypad.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
   LazyColumn(Modifier.heightIn(max=380.dp)){
    items((2..9).toList(),key={it}){key->val number=m.speedDial[key];val name=number?.let{n->m.contacts.firstOrNull{p->p.numbers.any{PhoneRules.input(it)==n}}?.name}
     Surface(onClick={if(number==null)select(key)else onCall(number)},shape=MaterialTheme.shapes.medium,color=colors.surfaceContainer,modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)){
      Row(Modifier.padding(start=12.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically){Text(key.toString(),Modifier.width(30.dp),style=MaterialTheme.typography.titleLarge,color=colors.primary);Column(Modifier.weight(1f).padding(horizontal=6.dp)){Text(name?:number?.let(PhoneRules::display)?:"Assign number",maxLines=1,overflow=TextOverflow.Ellipsis);if(name!=null)Text(PhoneRules.display(number),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
       if(number!=null)Box{IconButton(onClick={options=key}){Icon(Icons.Outlined.MoreVert,"Speed dial $key options")};DropdownMenu(expanded=options==key,onDismissRequest={options=null}){DropdownMenuItem(text={Text("Change")},onClick={options=null;select(key)});DropdownMenuItem(text={Text("Remove")},onClick={options=null;m.setSpeedDial(key,null)})}}
       else IconButton(onClick={select(key)}){Icon(Icons.Outlined.Add,"Assign speed dial $key")}
      }
     }
    }
   }
  }else{
   OutlinedTextField(query,{query=it.take(100)},placeholder={Text("Search contacts")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth())
   val current=PhoneRules.input(m.number)
   if(PhoneRules.callable(current)&&query.isBlank())TextButton(onClick={m.setSpeedDial(assign,current);select(null)}){Text("Use ${PhoneRules.display(current)}")}
   val people=remember(m.contacts,query){m.contacts.filter{p->p.name.contains(query,true)||p.numbers.any{PhoneRules.input(query).isNotBlank()&&PhoneRules.input(it).contains(PhoneRules.input(query))}}}
   LazyColumn(Modifier.heightIn(max=320.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
    if(people.isEmpty())item{Text(if(m.busy)"Loading contacts…"else"No matching contacts. You can also enter a number on the keypad, then assign it here.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
    items(people,key={it.id}){person->Column{Text(person.name,style=MaterialTheme.typography.titleSmall);person.numbers.distinct().forEach{raw->val n=PhoneRules.input(raw);if(PhoneRules.callable(n))TextButton(onClick={m.setSpeedDial(assign,n);select(null)},modifier=Modifier.fillMaxWidth()){Text(PhoneRules.display(n),Modifier.fillMaxWidth())}}}}
   }
  }
 }},confirmButton={TextButton(onClick={if(assign!=null)select(null)else close()}){Text(if(assign!=null)"Back"else"Done")}})
}
