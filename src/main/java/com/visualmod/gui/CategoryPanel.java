package com.visualmod.gui;

import com.visualmod.module.Module;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryPanel {
    private static final int BG_PANEL    = 0xF0111820;
    private static final int BG_MODULE   = 0xDD151E2A;
    private static final int BG_MOD_HOV  = 0xDD1C2C3E;
    private static final int ENABLED_BG  = 0xDD0A2030;
    private static final int ACCENT      = 0xFF38A8F5;
    private static final int ACCENT_D    = 0xFF9B6FFF;
    private static final int ACCENT_L    = 0xFF5BC8FF;
    private static final int ENABLED_DOT = 0xFF5BC8FF;
    private static final int TEXT_WHITE  = 0xFFEFF6FF;
    private static final int TEXT_DIM    = 0xFF5A7A96;
    private static final int TEXT_HOVER  = 0xFFB8D8F0;
    private static final int SEPARATOR   = 0x1AFFFFFF;
    private static final int BG_DARK     = 0xF00A0F15;

    private final Module.Category category;
    private final List<Module> allModules;
    private List<Module> visibleModules;
    private final int x, fixedY, width;
    private static final int HEADER_H=26, MODULE_H=20, PADDING_B=5;
    private int scrollOffset=0, maxVisibleH=300;
    private final Map<Module,Float> toggleAnim=new HashMap<>();
    private String filter="";

    public CategoryPanel(Module.Category category,List<Module> modules,int x,int y,int width){
        this.category=category;this.allModules=modules;this.visibleModules=new ArrayList<>(modules);
        this.x=x;this.fixedY=y;this.width=width;
        for(Module m:modules)toggleAnim.put(m,m.isEnabled()?1f:0f);
    }

    public void setFilter(String q){
        this.filter=q;
        if(q.isEmpty()){visibleModules=new ArrayList<>(allModules);}
        else{visibleModules=new ArrayList<>();for(Module m:allModules)if(com.visualmod.lang.Lang.matches(q,m.getName(),m.getDisplayName(),m.getDisplayDescription()))visibleModules.add(m);}
        scrollOffset=0;
    }

    public void scroll(int delta){
        int maxScroll=Math.max(0,visibleModules.size()*MODULE_H-maxVisibleH);
        scrollOffset=Math.max(-maxScroll,Math.min(0,scrollOffset+delta));
    }

    public boolean isMouseOver(int mx,int my){
        int totalH=HEADER_H+Math.min(visibleModules.size()*MODULE_H,maxVisibleH)+PADDING_B;
        return mx>=x&&mx<=x+width&&my>=fixedY&&my<=fixedY+totalH;
    }

    public void render(DrawContext ctx,TextRenderer tr,int mouseX,int mouseY,float openAge,int screenBottom){
        for(Module m:allModules){float cur=toggleAnim.getOrDefault(m,0f);float t=m.isEnabled()?1f:0f;toggleAnim.put(m,cur+(t-cur)*0.18f);}
        maxVisibleH=screenBottom-fixedY-HEADER_H-PADDING_B;
        int contentH=visibleModules.size()*MODULE_H,clampedH=Math.min(contentH,maxVisibleH),totalH=HEADER_H+clampedH+PADDING_B;
        int panelAlpha=(int)(0xF0*openAge);
        ctx.fill(x,fixedY,x+width,fixedY+totalH,blendA(BG_PANEL,panelAlpha));
        renderHeader(ctx,tr,openAge);
        int listTop=fixedY+HEADER_H,listBottom=listTop+clampedH;
        ctx.enableScissor(x,listTop,x+width,listBottom);
        for(int i=0;i<visibleModules.size();i++){
            Module mod=visibleModules.get(i);int mY=listTop+i*MODULE_H+scrollOffset;
            if(mY+MODULE_H<listTop||mY>listBottom)continue;
            boolean hovered=inside(mouseX,mouseY,x,mY,x+width,mY+MODULE_H);
            renderModule(ctx,tr,mod,mY,hovered,openAge);
        }
        ctx.disableScissor();
        if(contentH>maxVisibleH){int sbH=(int)((float)clampedH/contentH*clampedH),sbY=listTop+(int)((float)(-scrollOffset)/contentH*clampedH);
            ctx.fill(x+width-2,listTop,x+width,listBottom,0x33FFFFFF);ctx.fill(x+width-2,sbY,x+width,sbY+sbH,ACCENT);}
        int ba=(int)(0x44*openAge);drawBorder(ctx,x,fixedY,x+width,fixedY+totalH,(ba<<24)|0x88BBDD);
    }

    private void renderHeader(DrawContext ctx,TextRenderer tr,float age){
        ctx.fill(x,fixedY,x+width,fixedY+HEADER_H,BG_DARK);
        drawGradH(ctx,x,fixedY+HEADER_H-2,x+width,fixedY+HEADER_H,ACCENT,ACCENT_D);
        ctx.fill(x,fixedY,x+2,fixedY+HEADER_H,ACCENT);
        int nameColor=blendA(ACCENT_L,(int)(0xFF*age));
        ctx.drawTextWithShadow(tr,category.getDisplayName().toUpperCase(),x+9,fixedY+(HEADER_H-8)/2,nameColor);
        String cnt=filter.isEmpty()?String.valueOf(allModules.size()):visibleModules.size()+"/"+allModules.size();
        ctx.drawTextWithShadow(tr,cnt,x+width-tr.getWidth(cnt)-8,fixedY+(HEADER_H-8)/2,blendA(TEXT_DIM,(int)(0xFF*age)));
    }

    private void renderModule(DrawContext ctx,TextRenderer tr,Module mod,int mY,boolean hovered,float age){
        float anim=toggleAnim.getOrDefault(mod,0f);
        int bg=lerpC(hovered?BG_MOD_HOV:BG_MODULE,ENABLED_BG,anim);
        ctx.fill(x,mY,x+width,mY+MODULE_H,bg);
        int barW=(int)(3*anim);if(barW>0){ctx.fill(x,mY,x+barW,mY+MODULE_H/2,ACCENT);ctx.fill(x,mY+MODULE_H/2,x+barW,mY+MODULE_H,lerpC(ACCENT,ACCENT_D,0.3f));}
        ctx.fill(x+6,mY+MODULE_H-1,x+width-6,mY+MODULE_H,SEPARATOR);
        int textColor=mod.isEnabled()?lerpC(TEXT_WHITE,ACCENT_L,anim*0.4f):hovered?TEXT_HOVER:TEXT_DIM;
        textColor=blendA(textColor,(int)(0xFF*age));
        ctx.drawTextWithShadow(tr,mod.getDisplayName(),x+9,mY+(MODULE_H-8)/2,textColor);
        if(anim>0.05f){int dotA=(int)(0xFF*anim*age),dotCx=x+width-10,dotCy=mY+MODULE_H/2;
            ctx.fill(dotCx-4,dotCy-4,dotCx+4,dotCy+4,blendA(0x2238A8F5,dotA));
            ctx.fill(dotCx-2,dotCy-2,dotCx+2,dotCy+2,blendA(ENABLED_DOT,dotA));}
    }

    public Module getHoveredModule(int mx,int my){
        int listTop=fixedY+HEADER_H;
        for(int i=0;i<visibleModules.size();i++){int mY=listTop+i*MODULE_H+scrollOffset;if(inside(mx,my,x,mY,x+width,mY+MODULE_H))return visibleModules.get(i);}
        return null;
    }

    public boolean mouseClicked(double mx,double my,int btn){
        int listTop=fixedY+HEADER_H;
        for(int i=0;i<visibleModules.size();i++){int mY=listTop+i*MODULE_H+scrollOffset;if(inside((int)mx,(int)my,x,mY,x+width,mY+MODULE_H)){if(btn==0){visibleModules.get(i).toggle();return true;}}}
        return false;
    }
    public boolean mouseDragged(double mx,double my,int btn,double dx,double dy){return false;}
    public void mouseReleased(){}

    private static void drawBorder(DrawContext ctx,int x1,int y1,int x2,int y2,int color){ctx.fill(x1,y1,x2,y1+1,color);ctx.fill(x1,y2-1,x2,y2,color);ctx.fill(x1,y1,x1+1,y2,color);ctx.fill(x2-1,y1,x2,y2,color);}
    private static void drawGradH(DrawContext ctx,int x1,int y1,int x2,int y2,int cL,int cR){int s=16,w=x2-x1;for(int i=0;i<s;i++)ctx.fill(x1+i*w/s,y1,x1+(i+1)*w/s,y2,lerpC(cL,cR,i/(float)(s-1)));}
    private static boolean inside(int mx,int my,int x1,int y1,int x2,int y2){return mx>=x1&&mx<=x2&&my>=y1&&my<y2;}
    private static int lerpC(int a,int b,float t){int aA=(a>>24)&0xFF,aR=(a>>16)&0xFF,aG=(a>>8)&0xFF,aB=a&0xFF,bA=(b>>24)&0xFF,bR=(b>>16)&0xFF,bG=(b>>8)&0xFF,bB=b&0xFF;return((int)(aA+(bA-aA)*t)<<24)|((int)(aR+(bR-aR)*t)<<16)|((int)(aG+(bG-aG)*t)<<8)|(int)(aB+(bB-aB)*t);}
    private static int blendA(int color,int alpha){int o=(color>>24)&0xFF;return(color&0x00FFFFFF)|((o*alpha/255)<<24);}
}
