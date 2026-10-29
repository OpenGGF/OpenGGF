package com.openggf.game;

import com.openggf.i18n.UiMessages;
import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.graphics.PixelFont;
import com.openggf.util.Graphemes;
import com.openggf.graphics.text.HostText;
import com.openggf.graphics.text.HostTextContext;
import com.openggf.graphics.text.TextFace;
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
    private long languageRevision = UiMessages.revision();
    private List<Line> lines = List.of();
    private List<Button> buttons = List.of();
    private int width = 320, selected, scroll, bodyBottom, moreY, hintY, maxScroll, engineeringY;
    private boolean expanded, close;
    private InputHandler input;
    private int ticks;

    public MenuDetailsScreen(String title, String text) {
        this(new Content(title,"",text,Tone.INFO,List.of(),"",List.of(),""));
    }

    public MenuDetailsScreen(Content content) {
        this.content = Objects.requireNonNull(content);
        var choices = new ArrayList<>(content.actions());
        choices.add(new Action(UiMessages.text("menu.details.back"), () -> close = true, choices.isEmpty()));
        actions = choices;
        selected = moreOffset();
        for (int i=0;i<actions.size();i++) if (actions.get(i).primary()) { selected=moreOffset()+i; break; }
        layout();
    }

    private int moreOffset() { return content.more().isBlank() ? 0 : 1; }

    public boolean update(InputHandler input) {
        return update(input, MenuInput.pointer(input,width,224));
    }

    boolean update(InputHandler input, MenuInput.Pointer pointer) {
        if (input == null) return false;
        this.input=input;
        if (MenuInput.textBack(input)) { MenuFeedback.emit(MenuFeedback.Cue.CANCEL); return true; }
        int previous=selected, previousScroll=scroll, count=actions.size()+moreOffset();
        boolean textOnly=moreOffset()==0 && actions.size()==1;
        if (MenuInput.textUp(input)) { if(textOnly)scroll=Math.max(0,scroll-linePitch());else selected=Math.floorMod(selected-1,count); }
        if (MenuInput.textDown(input)) { if(textOnly)scroll=Math.min(maxScroll,scroll+linePitch());else selected=(selected+1)%count; }
        if (MenuInput.textLeft(input)) scroll=Math.max(0,scroll-60);
        if (MenuInput.textRight(input)) scroll=Math.min(maxScroll,scroll+60);
        boolean clicked=false;
        if (moreOffset()!=0 && pointer.over(12,moreY,width-24,buttonHeight()) && pointer.leftPressed()) { selected=0;clicked=true; }
        for (var button:buttons) if (pointer.over(button.x(),button.y(),button.width(),buttonHeight()) && pointer.leftPressed()) {
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
        if (languageRevision != UiMessages.revision()) {
            int last = actions.size() - 1;
            actions.set(last, new Action(UiMessages.text("menu.details.back"), () -> close = true, content.actions().isEmpty()));
            languageRevision = UiMessages.revision();
        }
        this.width=Math.max(320,width);layout();ticks++;
        MenuChrome.page(font,this.width,MenuStyle.pageText(content.title().toUpperCase(UiMessages.locale()),this.width-24,1,ticks),null,null);
        for (var line:lines) {
            int y=line.y()-scroll;
            if(y-MenuChrome.compactMetrics().above()<TOP || y+MenuChrome.compactMetrics().cellHeight()+MenuChrome.compactMetrics().below()>bodyBottom)continue;
            if(line.result()) {
                var icon=switch(content.tone()){case SUCCESS->MenuChrome.Icon.CHECK;case ERROR,WARNING->MenuChrome.Icon.WARNING;
                    case INFO->MenuChrome.Icon.INFO;};
                MenuChrome.icon(font,icon,15,y,toneColor());
            }
            float[] color=line.result()?toneColor():line.heading()?MenuChrome.CYAN:line.dim()?MenuChrome.DIM:MenuChrome.TEXT;
            MenuChrome.compact(font,line.text(),line.x(),y,color);
        }
        if(maxScroll>0) MenuChrome.scrollbar(font,this.width-8,TOP,bodyBottom-TOP,scroll,bodyBottom-TOP,maxScroll+bodyBottom-TOP);
        if(moreOffset()!=0) {
            if(selected==0)MenuChrome.focus(font,12,moreY,this.width-24,buttonHeight(),ticks);
            MenuChrome.compact(font,UiMessages.text("menu.details.more_toggle", java.util.Map.of("expanded", expanded ? "yes" : "no")),16,moreY+2,
                    selected==0?MenuChrome.WHITE:MenuChrome.MUTED);
        }
        if(!content.hint().isBlank())MenuChrome.compact(font,MenuStyle.pageText(content.hint(),this.width-30,MenuStyle.COMPACT,ticks),15,hintY,MenuChrome.MUTED);
        for(var button:buttons) {
            var action=button.action();
            MenuChrome.button(font,button.x(),button.y(),button.width(),buttonHeight(),MenuStyle.pageText(action.label(),button.width()-14,MenuStyle.COMPACT,ticks),selected==button.index(),
                    action.primary(),ticks);
        }
        boolean textOnly=moreOffset()==0 && actions.size()==1;
        var hints=new ArrayList<MenuChrome.Hint>();
        hints.add(MenuChrome.upDown(input,textOnly?UiMessages.text("menu.details.scroll"):UiMessages.text("menu.details.move")));
        if(maxScroll>0)hints.add(MenuChrome.leftRight(input,textOnly?UiMessages.text("menu.details.page"):UiMessages.text("menu.details.scroll")));
        hints.add(MenuChrome.hint(MenuChrome.confirmKey(input),UiMessages.text("menu.details.choose")));
        hints.add(MenuChrome.hint(MenuChrome.backKey(input),UiMessages.text("menu.details.back_caps")));
        MenuChrome.footer(font,this.width,null,hints,MenuChrome.controller(input),ticks);
    }

    private float[] toneColor() {
        return switch(content.tone()){case SUCCESS->MenuChrome.GREEN;case WARNING->MenuChrome.AMBER;case ERROR->MenuChrome.RED;
            case INFO->MenuChrome.CYAN;};
    }

    private static int buttonHeight() { return Math.max(12, MenuChrome.compactLineHeight()+3); }
    private static int linePitch() { return Math.max(PITCH, MenuChrome.compactLineHeight()+1); }

    private void layout() {
        var placed=new ArrayList<Button>();int x=15,row=0;
        for(int i=0;i<actions.size();i++) {
            int w=Math.min(width-30,Math.max(56,MenuChrome.compactWidth(actions.get(i).label())+16));
            if(x+w>width-15){row++;x=15;}
            placed.add(new Button(actions.get(i),x,row*(buttonHeight()+4),w,i+moreOffset()));x+=w+8;
        }
        int buttonTop=Math.min(200,MenuChrome.FOOTER_Y-4)-buttonHeight()-row*(buttonHeight()+4);
        buttons=placed.stream().map(b->new Button(b.action(),b.x(),b.y()+buttonTop,b.width(),b.index())).toList();
        hintY=buttonTop-MenuChrome.compactLineHeight()-5;
        moreY=buttonTop-buttonHeight()-4-(content.hint().isBlank()?0:MenuChrome.compactLineHeight()+4);
        bodyBottom=moreOffset()!=0?moreY-5:content.hint().isBlank()?buttonTop-7:hintY-MenuChrome.compactMetrics().above()-3;
        var text=new ArrayList<Line>();int y=TOP;
        if(MenuChrome.compactWidth(content.title())>width-24) { y=paragraph(text,content.title(),15,y,width-30,false);y+=4; }
        if(!content.result().isBlank()) {
            boolean first=true;
            for(String line:HostText.shared().wrap(readable(content.result()),width-42,TextFace.COMPACT,HostTextContext.current())) {
                text.add(new Line(line,27,y+MenuChrome.compactMetrics().above(),width-42,false,false,first));
                y+=linePitch();first=false;
            }
            y+=4;
        }
        if(!content.meaning().isBlank()) {y=paragraph(text,content.meaning(),15,y,width-30,false);y+=4;}
        for(var section:content.sections()) {
            if(section.rows().isEmpty())continue;
            if(!section.title().isBlank()) {
                for(String heading:MenuStyle.wrap(section.title(),width-30,MenuStyle.COMPACT)) {
                    text.add(new Line(heading,15,y+MenuChrome.compactMetrics().above(),width-30,true,false,false));y+=linePitch();
                }
            }
            // The value column starts after the section's widest label, never past 45% of the page.
            int widest=section.rows().stream().filter(r->!r.label().isBlank())
                    .mapToInt(r->MenuChrome.compactWidth(readable(r.label()))).max().orElse(0);
            int column=Math.min(Math.round(width*.45f),15+widest+12);
            for(var value:section.rows()) {
                if(value.label().isBlank())y=paragraph(text,value.value(),15,y,width-30,section.dim());
                else {
                    int labelEnd=paragraph(text,value.label(),15,y,column-23,true);
                    int valueEnd=paragraph(text,value.value(),column,y,width-column-15,section.dim());
                    y=Math.max(labelEnd,valueEnd);
                }
            }
            y+=4;
        }
        if(MenuChrome.compactWidth(content.hint())>width-30) { y=paragraph(text,content.hint(),15,y,width-30,false);y+=4; }
        for(var button:buttons) if(MenuChrome.compactWidth(button.action().label())>button.width()-14) {
            y=paragraph(text,button.action().label(),15,y,width-30,false);y+=4;
        }
        engineeringY=y;
        if(expanded) {text.add(new Line(UiMessages.text("menu.details.more"),15,y,width-30,true,false,false));y+=Math.max(12,linePitch());y=paragraph(text,content.more(),15,y,width-30,true);}
        lines=List.copyOf(text);maxScroll=Math.max(0,y-bodyBottom);scroll=Math.clamp(scroll,0,maxScroll);
    }

    private static int paragraph(List<Line> out,String value,int x,int y,int width,boolean dim) {
        for(var line:wrapText(readable(value),width)) {
            out.add(new Line(line,x,y+MenuChrome.compactMetrics().above(),width,false,dim,false));
            y+=linePitch();
        }
        return y;
    }

    /** Wrap compact menu text to a pixel width; see {@link MenuStyle#wrap}. */
    static List<String> wrapText(String text, int maxWidth) {
        return MenuStyle.wrap(text, maxWidth, MenuStyle.COMPACT);
    }

    /**
     * Text the menu fonts can draw stays as it is; each cluster that no bundled font can draw
     * faithfully keeps its identity as {@code [U+XXXX]} escapes. Never use this for persistence.
     */
    public static String readable(String value) {
        if(value.chars().allMatch(c->c>=32&&c<=126||c=='\n'||c=='\r'))return value;
        StringBuilder result=new StringBuilder();
        HostText text=HostText.shared();
        for(int start=0;start<value.length();) {
            int end=Graphemes.next(value,start);
            String cluster=value.substring(start,end);
            boolean lineBreak=cluster.chars().allMatch(c->c=='\n'||c=='\r');
            if(lineBreak||!cluster.chars().anyMatch(Character::isISOControl)
                    &&text.shape(cluster,TextFace.COMPACT,HostTextContext.current()).complete())result.append(cluster);
            else cluster.codePoints().forEach(c->result.append("[U+")
                    .append(Integer.toHexString(c).toUpperCase(java.util.Locale.ROOT)).append(']'));
            start=end;
        }
        return result.toString();
    }
}
