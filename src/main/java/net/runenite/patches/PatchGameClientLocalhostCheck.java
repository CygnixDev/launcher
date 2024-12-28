package net.runenite.patches;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import net.runenite.Patch;

@Slf4j
public class PatchGameClientLocalhostCheck extends Patch
{
	@Override
	public boolean appliesTo(String artifactName)
	{
		return artifactName.startsWith("injected-client-");
	}

	@Override
	public void apply(Artifact artifact, File workingDir)
	{
		try
		{
			overwriteLocalHost(workingDir.toPath());
		}
		catch (Exception e)
		{
			log.error("Error patching localhost check", e);
		}
	}

	protected void overwriteLocalHost(Path workingDir)
	{
		try (Stream<Path> paths = Files.walk(workingDir))
		{
			for (Path path : (Iterable<Path>) paths::iterator)
			{
				if (Files.isRegularFile(path))
				{
					try
					{
						boolean success = modifyFile(path.toFile(), bytes -> {
							int index = indexOf(bytes, "127.0.0.1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
							if (index != -1)
							{
								log.info("Attempting to patch 127.0.0.1 in file: " + path.getFileName());
								return patchLocalhost(bytes);
							}

							return null;
						});

						if (success)
						{
							return;
						}
					}
					catch (Exception e)
					{
						throw new RuntimeException(e);
					}
				}
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Unable to find 127.0.0.1.", e);
		}

		throw new IllegalStateException("Unable to find 127.0.0.1.");
	}

	protected byte[] patchLocalhost(byte[] bytes)
	{
		String searchInput = "127.0.0.1";
		String replacement = "";

		byte[] newSet = replaceText(bytes, searchInput, replacement);
		assert newSet != null;
		return newSet;
	}

	private byte[] replaceText(byte[] bytes, String input, String replacement)
	{
		byte[] searchBytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		int index = indexOf(bytes, searchBytes);
		if (index == -1)
		{
			return null;
		}

		return setString(bytes, index, replacement);
	}

	private byte[] setString(byte[] source, int stringStartIndex, String replacementString)
	{
		// Read the 2 bytes that store the old string length
		int oldLenByte1 = source[stringStartIndex - 2] & 0xFF;
		int oldLenByte2 = source[stringStartIndex - 1] & 0xFF;
		int oldLength = (oldLenByte1 << 8) | oldLenByte2;

		// Compute the length delta
		int newLength = replacementString.length();
		int lengthDelta = newLength - oldLength;

		// Verify that the new length fits in 2 bytes
		if (newLength >= 0xFFFF)
		{
			throw new IllegalArgumentException("String length must be between 0 and 65534.");
		}

		// Create the new byte array
		byte[] result = new byte[source.length + lengthDelta];

		// Copy everything up to the two length bytes
		System.arraycopy(source, 0, result, 0, stringStartIndex - 2);

		// Write the new length
		byte newSizeByte1 = (byte) ((newLength >>> 8) & 0xFF);
		byte newSizeByte2 = (byte) (newLength & 0xFF);
		result[stringStartIndex - 2] = newSizeByte1;
		result[stringStartIndex - 1] = newSizeByte2;

		// Write the replacement string bytes
		byte[] replacementBytes = replacementString.getBytes(StandardCharsets.UTF_8);
		System.arraycopy(replacementBytes, 0, result, stringStartIndex, replacementBytes.length);

		// Copy everything after the old string
		int copySrcPos = stringStartIndex + oldLength;
		int copyDestPos = stringStartIndex + newLength;
		int copyLength = source.length - copySrcPos;
		System.arraycopy(source, copySrcPos, result, copyDestPos, copyLength);

		return result;
	}
}
