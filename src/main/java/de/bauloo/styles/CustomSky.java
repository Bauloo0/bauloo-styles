package de.bauloo.styles;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sky styles without Skyboxify: the OptiFine sky files of the active packs
 * (assets/minecraft/optifine/sky/world0/skyN.properties + texture) drawn by the mod itself,
 * the way OptiFine draws them: a cube around the camera per layer, faded by the time of day,
 * turned with the sun, blended as the layer says. When Skyboxify (or another skybox mod) is
 * installed, that mod does it and this stays off.
 */
public final class CustomSky {

    private static final Pattern LAYER = Pattern.compile("optifine/sky/world0/sky(\\d+)\\.properties");
    private static final String[] OTHER_MODS = {"skyboxify", "fabricskyboxes", "nuit"};

    private enum Blend { ALPHA, ADD, SUBTRACT, MULTIPLY, DODGE, BURN, SCREEN, OVERLAY, REPLACE }

    private record Layer(int number, Identifier texture, Blend blend, boolean fades, int startFadeIn, int endFadeIn,
            int startFadeOut, int endFadeOut, boolean rotate, float speed, Vector3f axis,
            boolean clear, boolean rain, boolean thunder) {
    }

    /** a layer whose picture is loaded and waits for the render thread */
    private record Loaded(Layer layer, NativeImage image) {
    }

    private static final List<Layer> LAYERS = new ArrayList<>();
    private static final List<SkyTexture> TEXTURES = new ArrayList<>();
    /** a small copy of each layer's horizon square (ARGB, THUMB x THUMB), for the time slider's preview */
    private static final List<int[]> THUMBS = new ArrayList<>();
    public static final int THUMB = 32;
    private static int layersVersion;

    /** goes up when other sky layers are loaded */
    public static int layersVersion() {
        return layersVersion;
    }
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[Blend.values().length];
    private static GpuBuffer cube;
    private static boolean otherMod;

    private CustomSky() {
    }

    public static void register() {
        for (String mod : OTHER_MODS) {
            otherMod |= FabricLoader.getInstance().isModLoaded(mod);
        }
        if (otherMod) {
            BaulooStylesClient.LOGGER.info("[CustomSky] a skybox mod is installed, it draws the skies");
            return;
        }
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(BaulooStylesClient.MOD_ID, "custom_sky");
            }

            @Override
            public Collection<Identifier> getFabricDependencies() {
                return List.of(ResourceReloadListenerKeys.TEXTURES);
            }

            @Override
            public void reload(ResourceManager manager) {
                List<Loaded> loaded = read(manager);
                MinecraftClient.getInstance().send(() -> install(loaded));
            }
        });
    }

    /** true while the mod draws the skies (no other skybox mod) - the Sky tab works either way */
    public static boolean active() {
        return !otherMod;
    }

    // ------------------------------------------------------------------ reading the packs

    private static List<Loaded> read(ResourceManager manager) {
        List<Loaded> out = new ArrayList<>();
        Map<Identifier, Resource> found = manager.findResources("optifine/sky/world0",
                id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".properties"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            Matcher m = LAYER.matcher(e.getKey().getPath());
            if (!m.matches()) {
                continue;
            }
            try {
                Properties p = new Properties();
                try (var reader = new InputStreamReader(e.getValue().getInputStream(), StandardCharsets.UTF_8)) {
                    p.load(reader);
                }
                Identifier source = source(p.getProperty("source", "./sky" + m.group(1) + ".png").trim());
                if (source == null) {
                    continue;
                }
                var res = manager.getResource(source);
                if (res.isEmpty()) {
                    BaulooStylesClient.LOGGER.info("[CustomSky] {}: picture {} not found", e.getKey(), source);
                    continue;
                }
                NativeImage image;
                try (InputStream in = res.get().getInputStream()) {
                    image = NativeImage.read(in);
                }
                out.add(new Loaded(layer(Integer.parseInt(m.group(1)), source, p), image));
            } catch (Exception ex) {
                BaulooStylesClient.LOGGER.warn("[CustomSky] {} could not be read: {}", e.getKey(), ex.toString());
            }
        }
        out.sort((a, b) -> Integer.compare(a.layer().number(), b.layer().number()));
        return out;
    }

    /** "./x.png" next to the properties, "optifine/..." from assets/minecraft, or a full "ns:path" */
    private static Identifier source(String s) {
        s = s.replace('\\', '/');
        if (s.startsWith("./")) {
            s = "optifine/sky/world0/" + s.substring(2);
        } else if (s.startsWith("assets/minecraft/")) {
            s = s.substring("assets/minecraft/".length());
        }
        if (!s.endsWith(".png")) {
            s = s + ".png";
        }
        return Identifier.tryParse(s.toLowerCase(Locale.ROOT));
    }

    private static Layer layer(int number, Identifier texture, Properties p) {
        Blend blend;
        try {
            blend = Blend.valueOf(p.getProperty("blend", "add").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            blend = Blend.ADD;
        }
        int sIn = time(p.getProperty("startFadeIn"));
        int eIn = time(p.getProperty("endFadeIn"));
        int eOut = time(p.getProperty("endFadeOut"));
        boolean fades = sIn >= 0 && eIn >= 0 && eOut >= 0;
        int sOut = time(p.getProperty("startFadeOut"));
        if (fades && sOut < 0) {
            sOut = norm(eOut - norm(eIn - sIn));
        }
        Vector3f axis = new Vector3f(0, 0, 1);
        String a = p.getProperty("axis");
        if (a != null) {
            String[] parts = a.trim().split("\\s+");
            if (parts.length == 3) {
                try {
                    Vector3f v = new Vector3f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
                    if (v.lengthSquared() > 1.0E-6F) {
                        axis = v.normalize();
                    }
                } catch (NumberFormatException ignored) {
                    // keep the default axis
                }
            }
        }
        float speed = 1.0F;
        try {
            speed = Float.parseFloat(p.getProperty("speed", "1").trim());
        } catch (NumberFormatException ignored) {
            // keep 1
        }
        String weather = " " + p.getProperty("weather", "clear").toLowerCase(Locale.ROOT) + " ";
        return new Layer(number, texture, blend, fades, sIn, eIn, sOut, eOut,
                Boolean.parseBoolean(p.getProperty("rotate", "true").trim()), speed, axis,
                weather.contains(" clear "), weather.contains(" rain "), weather.contains(" thunder "));
    }

    /** "hh:mm" to ticks of the day, 06:00 = 0 (like OptiFine); -1 when missing */
    private static int time(String s) {
        if (s == null) {
            return -1;
        }
        String[] parts = s.trim().split(":");
        if (parts.length != 2) {
            return -1;
        }
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            return norm(h * 1000 + m * 1000 / 60 - 6000);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int norm(int t) {
        t %= 24000;
        return t < 0 ? t + 24000 : t;
    }

    // ------------------------------------------------------------------ GPU side (render thread)

    private static void install(List<Loaded> loaded) {
        for (SkyTexture t : TEXTURES) {
            t.close();
        }
        TEXTURES.clear();
        LAYERS.clear();
        THUMBS.clear();
        for (Loaded l : loaded) {
            try {
                TEXTURES.add(new SkyTexture(l.layer().texture(), l.image()));
                LAYERS.add(l.layer());
                THUMBS.add(thumb(l.image()));
            } catch (Exception e) {
                BaulooStylesClient.LOGGER.warn("[CustomSky] {} could not be uploaded: {}", l.layer().texture(), e.toString());
            } finally {
                l.image().close();
            }
        }
        layersVersion++;
        if (!LAYERS.isEmpty()) {
            BaulooStylesClient.LOGGER.info("[CustomSky] {} sky layers from the packs", LAYERS.size());
        }
    }

    /**
     * Called by the sky mixin before the sun and moon, with the vanilla sky already drawn.
     * matrices: the sky's own stack (the camera turn sits in the model-view stack).
     */
    private static boolean failedOnce;

    /** a problem while drawing: logged once with its trace */
    public static void failed(Throwable t) {
        if (!failedOnce) {
            failedOnce = true;
            BaulooStylesClient.LOGGER.warn("[CustomSky] drawing failed", t);
        }
    }

    public static void render(MatrixStack matrices, float sunAngle) {
        if (otherMod || LAYERS.isEmpty()) {
            return;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        // any world with the normal sky (servers have their own worlds like "plains"); the End has its own
        if (world == null || world.getDimension().skybox() != net.minecraft.world.dimension.DimensionType.Skybox.OVERWORLD) {
            return;
        }
        float tick = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        int day = (int) (world.getTimeOfDay() % 24000L);
        float rain = world.getRainGradient(tick);
        float thunder = world.getThunderGradient(tick);
        GpuBuffer vertices = cube();
        RenderSystem.ShapeIndexBuffer quads = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
        GpuBuffer indices = quads.getIndexBuffer(36);
        GpuTextureView color = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
        GpuTextureView depth = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
        for (int i = 0; i < LAYERS.size(); i++) {
            Layer layer = LAYERS.get(i);
            float b = fade(layer, day) * weather(layer, rain, thunder);
            if (b <= 1.0E-4F || (layer.blend() == Blend.REPLACE && b < 0.1F)) {
                continue;
            }
            Matrix4fStack mv = RenderSystem.getModelViewStack();
            mv.pushMatrix();
            mv.mul(matrices.peek().getPositionMatrix());
            mv.rotateY((float) Math.toRadians(-90.0));
            if (layer.rotate()) {
                mv.rotate(sunAngle * layer.speed(), layer.axis());
            }
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .write(mv, modulator(layer.blend(), b), new Vector3f(), new Matrix4f());
            SkyTexture texture = TEXTURES.get(i);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(() -> "Bauloo custom sky", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(pipeline(layer.blend()));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.bindTexture("Sampler0", texture.view, texture.sampler);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, quads.getIndexType());
                pass.drawIndexed(0, 0, 36, 1);
            }
            mv.popMatrix();
        }
    }

    /** the square (1, 1) of the 3 x 2 picture - a side the player looks at - shrunk to THUMB x THUMB */
    private static int[] thumb(NativeImage image) {
        int[] out = new int[THUMB * THUMB];
        int fw = image.getWidth() / 3, fh = image.getHeight() / 2;
        for (int y = 0; y < THUMB; y++) {
            for (int x = 0; x < THUMB; x++) {
                out[y * THUMB + x] = image.getColorArgb(fw + x * fw / THUMB, fh + y * fh / THUMB);
            }
        }
        return out;
    }

    /** true when the active packs have their own sky the mod draws */
    public static boolean hasSky() {
        return !otherMod && !LAYERS.isEmpty();
    }

    /**
     * The sky at a time of day as a small picture (THUMB x THUMB ARGB): the base colour of the hour
     * with every layer on top, faded and blended like in the world. For the time slider.
     */
    public static int[] preview(int ticks, int base) {
        float[] r = new float[THUMB * THUMB], g = new float[THUMB * THUMB], b = new float[THUMB * THUMB];
        java.util.Arrays.fill(r, ((base >> 16) & 0xFF) / 255F);
        java.util.Arrays.fill(g, ((base >> 8) & 0xFF) / 255F);
        java.util.Arrays.fill(b, (base & 0xFF) / 255F);
        for (int i = 0; i < LAYERS.size() && i < THUMBS.size(); i++) {
            Layer layer = LAYERS.get(i);
            float k = fade(layer, ticks) * weather(layer, 0, 0);
            if (k <= 1.0E-4F || (layer.blend() == Blend.REPLACE && k < 0.1F)) {
                continue;
            }
            int[] px = THUMBS.get(i);
            for (int p = 0; p < px.length; p++) {
                int c = px[p];
                float a = ((c >>> 24) & 0xFF) / 255F;
                float sr = ((c >> 16) & 0xFF) / 255F, sg = ((c >> 8) & 0xFF) / 255F, sb = (c & 0xFF) / 255F;
                switch (layer.blend()) {
                    case REPLACE -> { r[p] = sr; g[p] = sg; b[p] = sb; }
                    case ALPHA -> { float f = a * k; r[p] += (sr - r[p]) * f; g[p] += (sg - g[p]) * f; b[p] += (sb - b[p]) * f; }
                    case ADD -> { float f = a * k; r[p] += sr * f; g[p] += sg * f; b[p] += sb * f; }
                    case DODGE -> { r[p] += sr * k; g[p] += sg * k; b[p] += sb * k; }
                    case SUBTRACT -> { r[p] -= sr * k; g[p] -= sg * k; b[p] -= sb * k; }
                    case MULTIPLY -> { r[p] *= 1 - k + sr * k; g[p] *= 1 - k + sg * k; b[p] *= 1 - k + sb * k; }
                    case BURN -> { r[p] *= 1 - sr * k; g[p] *= 1 - sg * k; b[p] *= 1 - sb * k; }
                    case SCREEN -> { r[p] = r[p] + sr * k - r[p] * sr * k; g[p] = g[p] + sg * k - g[p] * sg * k; b[p] = b[p] + sb * k - b[p] * sb * k; }
                    case OVERLAY -> { r[p] = r[p] * (1 + sr * k); g[p] = g[p] * (1 + sg * k); b[p] = b[p] * (1 + sb * k); }
                }
            }
        }
        int[] out = new int[THUMB * THUMB];
        for (int p = 0; p < out.length; p++) {
            out[p] = 0xFF000000 | to255(r[p]) << 16 | to255(g[p]) << 8 | to255(b[p]);
        }
        return out;
    }

    private static int to255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    /** OptiFine's fade: full between endFadeIn and startFadeOut, ramps before and after */
    private static float fade(Layer l, int t) {
        if (!l.fades()) {
            return 1.0F;
        }
        if (between(t, l.endFadeIn(), l.startFadeOut())) {
            return 1.0F;
        }
        if (between(t, l.startFadeIn(), l.endFadeIn())) {
            int len = norm(l.endFadeIn() - l.startFadeIn());
            return len == 0 ? 1.0F : norm(t - l.startFadeIn()) / (float) len;
        }
        if (between(t, l.startFadeOut(), l.endFadeOut())) {
            int len = norm(l.endFadeOut() - l.startFadeOut());
            return len == 0 ? 0.0F : 1.0F - norm(t - l.startFadeOut()) / (float) len;
        }
        return 0.0F;
    }

    private static boolean between(int t, int a, int b) {
        return a <= b ? t >= a && t <= b : t >= a || t <= b;
    }

    private static float weather(Layer l, float rain, float thunder) {
        float b = 0;
        if (l.clear()) {
            b += 1.0F - rain;
        }
        if (l.rain()) {
            b += rain - thunder;
        }
        if (l.thunder()) {
            b += thunder;
        }
        return Math.max(0.0F, Math.min(1.0F, b));
    }

    /** the colour each blend mode is drawn with, like OptiFine's Blender */
    private static Vector4f modulator(Blend blend, float b) {
        return switch (blend) {
            case ALPHA, ADD, REPLACE -> new Vector4f(1, 1, 1, b);
            case MULTIPLY -> new Vector4f(b, b, b, b);
            default -> new Vector4f(b, b, b, 1);
        };
    }

    private static RenderPipeline pipeline(Blend blend) {
        RenderPipeline p = PIPELINES[blend.ordinal()];
        if (p != null) {
            return p;
        }
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of(BaulooStylesClient.MOD_ID, "pipeline/custom_sky_" + blend.name().toLowerCase(Locale.ROOT)))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0")
                .withDepthWrite(false)
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS);
        switch (blend) {
            case REPLACE -> builder.withoutBlend();
            case ALPHA -> builder.withBlend(BlendFunction.TRANSLUCENT);
            case ADD -> builder.withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE));
            case SUBTRACT -> builder.withBlend(new BlendFunction(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO));
            case MULTIPLY -> builder.withBlend(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ONE_MINUS_SRC_ALPHA));
            case DODGE -> builder.withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE));
            case BURN -> builder.withBlend(new BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR));
            case SCREEN -> builder.withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR));
            case OVERLAY -> builder.withBlend(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR));
        }
        p = builder.build();
        PIPELINES[blend.ordinal()] = p;
        return p;
    }

    /**
     * The six sides, in OptiFine's order and turns: the picture is 3 x 2 squares,
     * side n uses the square (n % 3, n / 3).
     */
    private static GpuBuffer cube() {
        if (cube != null) {
            return cube;
        }
        try (BufferAllocator allocator = BufferAllocator.fixedSized(24 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
            BufferBuilder bb = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            Matrix4f m = new Matrix4f().rotateX(rad(90)).rotateZ(rad(-90));
            side(bb, new Matrix4f(m), 4);
            side(bb, new Matrix4f(m).rotateX(rad(90)), 1);
            side(bb, new Matrix4f(m).rotateX(rad(-90)), 0);
            m.rotateZ(rad(90));
            side(bb, new Matrix4f(m), 5);
            m.rotateZ(rad(90));
            side(bb, new Matrix4f(m), 2);
            m.rotateZ(rad(90));
            side(bb, new Matrix4f(m), 3);
            try (BuiltBuffer built = bb.end()) {
                cube = RenderSystem.getDevice().createBuffer(() -> "Bauloo custom sky cube", GpuBuffer.USAGE_VERTEX, built.getBuffer());
            }
        }
        return cube;
    }

    private static void side(BufferBuilder bb, Matrix4f m, int side) {
        float tx = (side % 3) / 3.0F;
        float ty = (side / 3) / 2.0F;
        float w = 1.0F / 3.0F;
        bb.vertex(m, -100, -100, -100).texture(tx, ty).color(-1);
        bb.vertex(m, -100, -100, 100).texture(tx, ty + 0.5F).color(-1);
        bb.vertex(m, 100, -100, 100).texture(tx + w, ty + 0.5F).color(-1);
        bb.vertex(m, 100, -100, -100).texture(tx + w, ty).color(-1);
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    /** one layer's picture on the GPU, smooth sampling */
    private static final class SkyTexture {
        final GpuTexture texture;
        final GpuTextureView view;
        final net.minecraft.client.gl.GpuSampler sampler;

        SkyTexture(Identifier id, NativeImage image) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "Bauloo sky " + id, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);
            view = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
            device.createCommandEncoder().writeToTexture(texture, image, 0, 0, 0, 0, image.getWidth(), image.getHeight(), 0, 0);
        }

        void close() {
            view.close();
            texture.close();
        }
    }
}
