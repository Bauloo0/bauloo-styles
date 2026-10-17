package de.bauloo.styles;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.InputUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The key that opens the styles menu. Set it in the menu itself; it is kept in
 * config/bauloo-styles.txt so it survives a restart.
 *
 * Made by bauloo.
 */
public final class StylesHotkey {

	private static InputUtil.Key key = InputUtil.UNKNOWN_KEY;
	private static boolean loaded;

	private StylesHotkey() {
	}

	public static InputUtil.Key get() {
		if (!loaded) {
			load();
		}
		return key;
	}

	public static void set(InputUtil.Key value) {
		key = value == null ? InputUtil.UNKNOWN_KEY : value;
		loaded = true;
		save();
	}

	public static boolean isSet() {
		InputUtil.Key k = get();
		return k != null && !k.equals(InputUtil.UNKNOWN_KEY) && k.getCode() >= 0;
	}

	/** what the button in the menu shows */
	public static String label() {
		return isSet() ? get().getLocalizedText().getString() : "None";
	}

	// ------------------------------------------------------------- storage

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("bauloo-styles.txt");
	}

	private static void save() {
		try {
			Files.writeString(file(), encode(key));
		} catch (IOException ignored) {
		}
	}

	private static void load() {
		loaded = true;
		try {
			Path f = file();
			if (Files.exists(f)) {
				key = decode(Files.readString(f).trim());
			} else {
				// first start: Right Shift opens the menu, so nobody needs Mod Menu to get in (changeable in the menu)
				key = InputUtil.Type.KEYSYM.createFromCode(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
			}
		} catch (IOException ignored) {
			key = InputUtil.UNKNOWN_KEY;
		}
	}

	private static String encode(InputUtil.Key k) {
		if (k == null || k.equals(InputUtil.UNKNOWN_KEY)) {
			return "NONE";
		}
		return (k.getCategory() == InputUtil.Type.MOUSE ? "MOUSE" : "KEYSYM") + ":" + k.getCode();
	}

	private static InputUtil.Key decode(String s) {
		if (s == null || s.equals("NONE")) {
			return InputUtil.UNKNOWN_KEY;
		}
		try {
			String[] parts = s.split(":");
			InputUtil.Type type = "MOUSE".equals(parts[0]) ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM;
			return type.createFromCode(Integer.parseInt(parts[1]));
		} catch (Exception e) {
			return InputUtil.UNKNOWN_KEY;
		}
	}
}
