use onklav_ui::{forms::{form,FormField},serde_json::Value};
fn step(input:Value)->Value{
    form("Task form",input,&[
        FormField::text("task","Task").required(),
        FormField::multiline("details","Details"),
        FormField::toggle("done","Completed",false),
        FormField::choice("priority","Priority",&[("normal","Normal"),("high","High")]),
    ])
}
onklav_ui::app!(step);
