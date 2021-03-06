package dev.vatuu.eulla.render;

import dev.vatuu.eulla.WorldPortals;
import dev.vatuu.eulla.extensions.WorldRendererExt;
import dev.vatuu.eulla.portals.WorldPortal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.Matrix4f;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static com.mojang.blaze3d.platform.GlStateManager.getAttribLocation;
import static com.mojang.blaze3d.platform.GlStateManager.getUniformLocation;
import static com.mojang.blaze3d.systems.RenderSystem.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class WorldPortalRenderer {

    private FloatBuffer buffer = BufferUtils.createFloatBuffer(128);

    private int mvpUniformLocation;
    private int texUniformLocation;
    private int attributePos;

    private int vbo;
    private int vao;

    public void onShaderReload() {
        destroy();
        mvpUniformLocation = getUniformLocation(Shaders.PORTAL_SHADER, "mvp");
        texUniformLocation = getUniformLocation(Shaders.PORTAL_SHADER, "portal");
        attributePos = getAttribLocation(Shaders.PORTAL_SHADER, "pos");
    }

    private void init() {
        destroy();
        vbo = glGenBuffers();
        vao = glGenVertexArrays();

        glUseProgram(Shaders.PORTAL_SHADER);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindVertexArray(vao);

        buffer.clear();
        buffer.put(-0.5f).put(-0.5f).put(0);
        buffer.put(0.5f).put(-0.5f).put(0);
        buffer.put(0.5f).put(0.5f).put(0);

        buffer.put(-0.5f).put(-0.5f).put(0);
        buffer.put(0.5f).put(0.5f).put(0);
        buffer.put(-0.5f).put(0.5f).put(0);
        buffer.flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        glVertexAttribPointer(attributePos, 3, GL_FLOAT, false, 0, 0);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glUseProgram(0);
    }

    public void render(MatrixStack model, Matrix4f projection, float delta) {
        WorldPortals.INSTANCE.getPortalManager().getPortals().forEach(p -> render(p, model, projection, delta));
    }

    private void render(WorldPortal portal, MatrixStack model, Matrix4f projection, float delta) {
        if (vbo == 0) init();

        portal.renderPortalView(model, delta);
        Framebuffer portalView = portal.getView();

        glUseProgram(Shaders.PORTAL_SHADER);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindVertexArray(vao);

        enableCull();
        enableDepthTest();
        depthMask(true);

        model.push();

        buffer.clear();
        Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        model.translate(portal.getPosition().x - camPos.x, portal.getPosition().y - camPos.y, portal.getPosition().z - camPos.z);
        model.multiply(new Quaternion(new Vector3f(1, 0, 0), portal.getYaw(), true));
        model.multiply(new Quaternion(new Vector3f(0, 1, 0), portal.getPitch(), true));
        model.scale(portal.getWidth(), portal.getHeight(), 1);
        Matrix4f mvp = projection.copy();
        mvp.multiply(model.peek().getModel());
        mvp.writeToBuffer(buffer);
        buffer.position(16);
        buffer.flip();
        glUniformMatrix4fv(mvpUniformLocation, false, buffer);

        portalView.beginRead();
        glUniform1i(texUniformLocation, 0);

        glEnableVertexAttribArray(attributePos);

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glDisableVertexAttribArray(attributePos);

        model.pop();
        portalView.endRead();

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glUseProgram(0);
    }

    public void destroy() {
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);

        vbo = vao = 0;
    }

    public static WorldPortalRenderer getInstance(WorldRenderer wr) {
        return ((WorldRendererExt) wr).getWorldPortalRenderer();
    }

}
