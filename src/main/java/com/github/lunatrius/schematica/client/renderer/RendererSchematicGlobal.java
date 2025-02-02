package com.github.lunatrius.schematica.client.renderer;

import com.github.lunatrius.core.util.vector.Vector3d;
import com.github.lunatrius.schematica.client.world.SchematicWorld;
import com.github.lunatrius.schematica.proxy.ClientProxy;
import com.github.lunatrius.schematica.reference.Constants;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.profiler.Profiler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

public class RendererSchematicGlobal {
    public static final RendererSchematicGlobal INSTANCE = new RendererSchematicGlobal();

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Profiler profiler = this.minecraft.mcProfiler;

    private final Frustrum frustrum = new Frustrum();
    public RenderBlocks renderBlocks = null;
    public final List<RendererSchematicChunk> sortedRendererSchematicChunk = new ArrayList<RendererSchematicChunk>();
    private final RendererSchematicChunkComparator rendererSchematicChunkComparator =
            new RendererSchematicChunkComparator();

    private RendererSchematicGlobal() {}

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        EntityPlayerSP player = this.minecraft.thePlayer;
        if (player != null) {
            ClientProxy.setPlayerData(player, event.partialTicks);

            this.profiler.startSection("schematica");
            SchematicWorld schematic = ClientProxy.schematic;
            if ((schematic != null && schematic.isRendering) || ClientProxy.isRenderingGuide) {
                render(schematic);
            }

            this.profiler.endSection();
        }
    }

    public void render(SchematicWorld schematic) {
        GL11.glPushMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);

        Vector3d playerPosition = ClientProxy.playerPosition.clone();
        Vector3d extra = new Vector3d();
        if (schematic != null) {
            extra.add(schematic.position.toVector3d());
            playerPosition.sub(extra);
        }

        GL11.glTranslated(-playerPosition.x, -playerPosition.y, -playerPosition.z);

        this.profiler.startSection("schematic");
        if (schematic != null && schematic.isRendering) {
            this.profiler.startSection("updateFrustrum");
            updateFrustrum(schematic);

            this.profiler.endStartSection("sortAndUpdate");
            if (RendererSchematicChunk.getCanUpdate()) {
                sortAndUpdate(schematic);
            }

            this.profiler.endStartSection("render");
            int pass;
            for (pass = 0; pass < 3; pass++) {
                for (RendererSchematicChunk renderer : this.sortedRendererSchematicChunk) {
                    renderer.render(pass);
                }
            }
            this.profiler.endSection();
        }

        this.profiler.endStartSection("guide");

        RenderHelper.createBuffers();

        this.profiler.startSection("dataPrep");
        if (schematic != null && schematic.isRendering) {
            RenderHelper.drawCuboidOutline(
                    RenderHelper.VEC_ZERO, schematic.dimensions(), RenderHelper.LINE_ALL, 0.75f, 0.0f, 0.75f, 0.25f);
        }

        if (ClientProxy.isRenderingGuide) {
            Vector3d start = new Vector3d();
            Vector3d end = new Vector3d();

            ClientProxy.pointMin.toVector3d(start).sub(extra);
            ClientProxy.pointMax.toVector3d(end).sub(extra).add(1, 1, 1);
            RenderHelper.drawCuboidOutline(
                    start.toVector3f(), end.toVector3f(), RenderHelper.LINE_ALL, 0.0f, 0.75f, 0.0f, 0.25f);

            ClientProxy.pointA.toVector3d(start).sub(extra);
            end.set(start).add(1, 1, 1);
            RenderHelper.drawCuboidOutline(
                    start.toVector3f(), end.toVector3f(), RenderHelper.LINE_ALL, 0.75f, 0.0f, 0.0f, 0.25f);
            RenderHelper.drawCuboidSurface(
                    start.toVector3f(), end.toVector3f(), RenderHelper.QUAD_ALL, 0.75f, 0.0f, 0.0f, 0.25f);

            ClientProxy.pointB.toVector3d(start).sub(extra);
            end.set(start).add(1, 1, 1);
            RenderHelper.drawCuboidOutline(
                    start.toVector3f(), end.toVector3f(), RenderHelper.LINE_ALL, 0.0f, 0.0f, 0.75f, 0.25f);
            RenderHelper.drawCuboidSurface(
                    start.toVector3f(), end.toVector3f(), RenderHelper.QUAD_ALL, 0.0f, 0.0f, 0.75f, 0.25f);
        }

        int quadCount = RenderHelper.getQuadCount();
        int lineCount = RenderHelper.getLineCount();

        if (quadCount > 0 || lineCount > 0) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            GL11.glLineWidth(1.5f);

            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

            this.profiler.endStartSection("quad");
            if (quadCount > 0) {
                GL11.glVertexPointer(3, 0, RenderHelper.getQuadVertexBuffer());
                GL11.glColorPointer(4, 0, RenderHelper.getQuadColorBuffer());
                GL11.glDrawArrays(GL11.GL_QUADS, 0, quadCount);
            }

            this.profiler.endStartSection("line");
            if (lineCount > 0) {
                GL11.glVertexPointer(3, 0, RenderHelper.getLineVertexBuffer());
                GL11.glColorPointer(4, 0, RenderHelper.getLineColorBuffer());
                GL11.glDrawArrays(GL11.GL_LINES, 0, lineCount);
            }

            this.profiler.endSection();

            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }

        this.profiler.endSection();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    private void updateFrustrum(SchematicWorld schematic) {
        this.frustrum.setPosition(
                ClientProxy.playerPosition.x - schematic.position.x,
                ClientProxy.playerPosition.y - schematic.position.y,
                ClientProxy.playerPosition.z - schematic.position.z);
        for (RendererSchematicChunk rendererSchematicChunk : this.sortedRendererSchematicChunk) {
            rendererSchematicChunk.isInFrustrum =
                    this.frustrum.isBoundingBoxInFrustum(rendererSchematicChunk.getBoundingBox());
        }
    }

    private void sortAndUpdate(SchematicWorld schematic) {
        this.rendererSchematicChunkComparator.setPosition(schematic.position);
        Collections.sort(this.sortedRendererSchematicChunk, this.rendererSchematicChunkComparator);

        for (RendererSchematicChunk rendererSchematicChunk : this.sortedRendererSchematicChunk) {
            if (rendererSchematicChunk.getDirty()) {
                rendererSchematicChunk.updateRenderer();
                break;
            }
        }
    }

    public void createRendererSchematicChunks(SchematicWorld schematic) {
        int width = (schematic.getWidth() - 1) / Constants.SchematicChunk.WIDTH + 1;
        int height = (schematic.getHeight() - 1) / Constants.SchematicChunk.HEIGHT + 1;
        int length = (schematic.getLength() - 1) / Constants.SchematicChunk.LENGTH + 1;

        destroyRendererSchematicChunks();

        this.renderBlocks = new RenderBlocks(schematic);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    this.sortedRendererSchematicChunk.add(new RendererSchematicChunk(schematic, x, y, z));
                }
            }
        }
    }

    public void destroyRendererSchematicChunks() {
        this.renderBlocks = null;
        while (this.sortedRendererSchematicChunk.size() > 0) {
            this.sortedRendererSchematicChunk.remove(0).delete();
        }
    }

    public void refresh() {
        for (RendererSchematicChunk renderer : this.sortedRendererSchematicChunk) {
            renderer.setDirty();
        }
    }
}
