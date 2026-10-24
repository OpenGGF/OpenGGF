package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared compact help panel. Readable sections and actions stay separate from full diagnostics. */
public final class MenuDetailsScreen {
    public enum Tone { INFO, SUCCESS, WARNING, ERROR }
    public record Row(String label, String value) { }
    public record Section(String title, List<Row> rows, boolean dim) {
        public Section { rows = List.copyOf(rows); }
    }
    public record Action(String label, Runnable callback, boolean primary) {
        public Action { Objects.requireNonNull(label); Objects.requireNonNull(callback); }
    }
    public record Content(String title, String result, String meaning, Tone tone,
                          List<Section> sections, String more, List<Action> actions, String hint) {
        public Content {
            Objects.requireNonNull(title); Objects.requireNonNull(result); Objects.requireNonNull(meaning);
            Objects.requireNonNull(tone); Objects.requireNonNull(more); Objects.requireNonNull(hint);
            sections = List.copyOf(sections); actions = List.copyOf(actions);
        }
    }
    private record Line(String text, int x, int y, int width, boolean heading, boolean dim, boolean result) { }
    private record Button(Action action, int x, int y, int width, int index) { }
    private static final int TOP = 35, PITCH = 10;
    private final Content content;
    private final List<Action> actions;
    private List<Line> lines = List.of();
    private List<Button> buttons = List.of();
    private int width = 320, selected, scroll, bodyBottom, moreY, hintY, maxScroll, engineeringY;
    private boolean expanded, close;
    private String back = "Esc", accept = "Enter", directions = "↑↓", horizontal = "←→";

    public MenuDetailsScreen(String title, String text) {
        this(new Content(title,"",text,Tone.INFO,List.of(),"",List.of(),""));
    }

    public MenuDetailsScreen(Content content) {
        this.content = Objects.requireNonNull(content);
        var choices = new ArrayList<>(content.actions());
        choices.add(new Action("Back", () -> close = true, choices.isEmpty()));
        actions = List.copyOf(choices);
        selected = moreOffset();
        for (int i=0;i<actions.size();i++) if (actions.get(i).primary()) { selected=moreOffset()+i; break; }
        layout();
    }

    private int moreOffset() { return content.more().isBlank() ? 0 : 1; }

    public boolean update(InputHandler input) {
        if (input == null) return false;
        back=MenuInput.backLabel(input); accept=MenuInput.confirmLabel(input);
        directions=MenuInput.controller(input)?MenuInput.directionLabel(input):"↑↓";
        horizontal=MenuInput.controller(input)?"L/R":"←→";
        if (MenuInput.textBack(input)) { MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return true; }
        int previous=selected, previousScroll=scroll, count=actions.size()+moreOffset();
        boolean textOnly=moreOffset()==0 && actions.size()==1;
        if (MenuInput.textUp(input)) { if(textOnly)scroll=Math.max(0,scroll-PITCH);else selected=Math.floorMod(selected-1,count); }
        if (MenuInput.textDown(input)) { if(textOnly)scroll=Math.min(maxScroll,scroll+PITCH);else selected=(selected+1)%count; }
        if (MenuInput.textLeft(input)) scroll=Math.max(0,scroll-60);
        if (MenuInput.textRight(input)) scroll=Math.min(maxScroll,scroll+60);
        var pointer=MenuInput.pointer(input,width,224);
        boolean clicked=false;
        if (moreOffset()!=0 && pointer.over(12,moreY,width-24,12) && pointer.leftPressed()) { selected=0;clicked=true; }
        for (var button:buttons) if (pointer.over(button.x(),button.y(),button.width(),12) && pointer.leftPressed()) {
            selected=button.index();clicked=true;
        }
        if (previous!=selected || previousScroll!=scroll) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        if (MenuInput.textAccept(input) || clicked) {
            if (moreOffset()!=0 && selected==0) {
                expanded=!expanded;layout();scroll=expanded?Math.min(maxScroll,engineeringY-TOP):0;
            } else actions.get(selected-moreOffset()).callback().run();
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        }
        return close;
    }

    public void render(PixelFont font, int width) {
        if (font == null) return;
        this.width=Math.max(320,width);layout();
        MenuStyle.fill(font,0,0,this.width,224,.025f,.04f,.075f,1);
        for(int y=29,row=0;y<207;y+=16,row++) for(int x=(row&1)*16;x<this.width;x+=32)
            MenuStyle.fill(font,x,y,Math.min(15,this.width-x),Math.min(15,207-y),.031f,.048f,.087f,1);
        MenuStyle.text(font,content.title(),12,10,this.width-24,.94f,.97f,1);
        MenuStyle.fill(font,12,25,this.width-24,1,1,.78f,.23f,1);
        for (var line:lines) {
            int y=line.y()-scroll;
            if(y<TOP || y+8>bodyBottom)continue;
            if(line.result()) {
                String mark=switch(content.tone()){case SUCCESS->"✓";case ERROR,WARNING->"✗";case INFO->"•";};
                MenuStyle.text(font,mark,15,y,8,content.tone()==Tone.SUCCESS?.38f:1,
                        content.tone()==Tone.SUCCESS?.9f:.47f,content.tone()==Tone.SUCCESS?.93f:.53f);
            }
            MenuStyle.text(font,line.text(),line.x(),y,line.width(),line.heading()?1:line.dim()?.43f:.88f,
                    line.heading()?.78f:line.dim()?.51f:.92f,line.heading()?.23f:line.dim()?.61f:.98f);
        }
        if(moreOffset()!=0) {
            if(selected==0)MenuStyle.fill(font,12,moreY,this.width-24,12,.045f,.075f,.115f,1);
            MenuStyle.text(font,(expanded?"- ":"+ ")+"More",15,moreY+2,this.width-30,.43f,.51f,.61f);
        }
        if(!content.hint().isBlank())MenuStyle.text(font,content.hint(),15,hintY,this.width-30,.43f,.51f,.61f);
        for(var button:buttons) {
            var action=button.action();
            MenuStyle.fill(font,button.x(),button.y(),button.width(),12,action.primary()?1:.06f,action.primary()?.78f:.095f,action.primary()?.23f:.14f,1);
            if(selected==button.index())MenuStyle.fill(font,button.x()-3,button.y(),2,12,.4f,.86f,1,1);
            MenuStyle.text(font,action.label(),button.x()+7,button.y()+2,button.width()-14,
                    action.primary()?.025f:.87f,action.primary()?.05f:.91f,action.primary()?.09f:.96f);
        }
        MenuStyle.fill(font,0,207,this.width,17,.018f,.03f,.055f,1);
        String footer=directions+(moreOffset()==0 && actions.size()==1?" Scroll  "+horizontal+" Page  ":" Move  "+horizontal+" Scroll  ")+accept+" Choose  "+back+" Back";
        if(!MenuStyle.fit(footer,this.width-18).equals(footer))footer=footer.replace("  "," ");
        MenuStyle.text(font,footer,9,213,this.width-18,.43f,.51f,.61f);
    }

    private void layout() {
        var placed=new ArrayList<Button>();int x=15,row=0;
        for(int i=0;i<actions.size();i++) {
            int w=Math.min(width-30,actions.get(i).label().length()*6+14);
            if(x+w>width-15){row++;x=15;}
            placed.add(new Button(actions.get(i),x,row*15,w,i+moreOffset()));x+=w+8;
        }
        int buttonTop=192-row*15;
        buttons=placed.stream().map(b->new Button(b.action(),b.x(),b.y()+buttonTop,b.width(),b.index())).toList();
        hintY=buttonTop-13;
        moreY=buttonTop-16-(content.hint().isBlank()?0:12);
        bodyBottom=moreOffset()!=0?moreY-5:buttonTop-7;
        var text=new ArrayList<Line>();int y=TOP;
        if(!content.result().isBlank()) { text.add(new Line(readable(content.result()),27,y,width-42,false,false,true));y+=14; }
        if(!content.meaning().isBlank()) {y=paragraph(text,content.meaning(),15,y,width-30,false);y+=4;}
        for(var section:content.sections()) {
            if(section.rows().isEmpty())continue;
            if(!section.title().isBlank()){text.add(new Line(section.title(),15,y,width-30,true,false,false));y+=12;}
            for(var value:section.rows()) {
                if(value.label().isBlank())y=paragraph(text,value.value(),15,y,width-30,section.dim());
                else {
                    int column=Math.round(width*.45f);
                    int labelEnd=paragraph(text,value.label(),15,y,column-23,true);
                    int valueEnd=paragraph(text,value.value(),column,y,width-column-15,section.dim());
                    y=Math.max(labelEnd,valueEnd);
                }
            }
            y+=4;
        }
        engineeringY=y;
        if(expanded) {text.add(new Line("More",15,y,width-30,true,false,false));y+=12;y=paragraph(text,content.more(),15,y,width-30,true);}
        lines=List.copyOf(text);maxScroll=Math.max(0,y-bodyBottom);scroll=Math.clamp(scroll,0,maxScroll);
    }

    private static int paragraph(List<Line> out,String value,int x,int y,int width,boolean dim) {
        for(var line:wrapText(readable(value),Math.max(1,width/6))) {out.add(new Line(line,x,y,width,false,dim,false));y+=PITCH;}
        return y;
    }

    static List<String> wrapText(String text, int columns) {
        List<String> result=new ArrayList<>();
        for(String paragraph:text.split("\\R",-1)) {
            String remaining=paragraph;
            while(remaining.length()>columns) {
                int end=remaining.lastIndexOf(' ',columns);if(end<=0)end=columns;
                result.add(remaining.substring(0,end));remaining=remaining.substring(end).stripLeading();
            }
            result.add(remaining);
        }
        return List.copyOf(result);
    }

    /** Unsupported glyphs retain their identity; never use this representation for persistence. */
    public static String readable(String value) {
        StringBuilder result=new StringBuilder();
        value.codePoints().forEach(c->{
            if(c>=32&&c<=126||c=='\n'||c=='\r'||c>=0x2190&&c<=0x2193||c==0x2713||c==0x2717||c==0xB7||c==0x2022)result.appendCodePoint(c);
            else result.append("[U+").append(Integer.toHexString(c).toUpperCase(java.util.Locale.ROOT)).append(']');
        });
        return result.toString();
    }
}
