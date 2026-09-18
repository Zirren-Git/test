package io.github.zirren.chatterbox.config;

import java.util.Objects;

/**
 * A static text shortcut, e.g. {@code {shrug}} -> {@code ¯\_(ツ)_/¯}.
 * Fully user-editable via the config screen.
 */
public class Shortcut {
	/** Token without braces, lowercase letters/digits/underscore, e.g. {@code shrug}. */
	public String token = "";
	/** Replacement text. */
	public String replacement = "";
	public boolean enabled = true;

	public Shortcut() {
	}

	public Shortcut(String token, String replacement) {
		this(token, replacement, true);
	}

	public Shortcut(String token, String replacement, boolean enabled) {
		this.token = token;
		this.replacement = replacement;
		this.enabled = enabled;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Shortcut that)) return false;
		return Objects.equals(token, that.token)
				&& Objects.equals(replacement, that.replacement)
				&& enabled == that.enabled;
	}

	@Override
	public int hashCode() {
		return Objects.hash(token, replacement, enabled);
	}
}
