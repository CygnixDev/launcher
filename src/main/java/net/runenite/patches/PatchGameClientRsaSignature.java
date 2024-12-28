package net.runenite.patches;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import static net.runenite.ArtifactPatcher.RESOURCES_DIR;
import net.runenite.Patch;
import net.runenite.utils.PrivateKeyReader;

@Slf4j
public class PatchGameClientRsaSignature extends Patch
{
	public static final File NEW_RSA_KEY = new File(RESOURCES_DIR, "injected-client/key.rsa");

	@Override
	public boolean appliesTo(String artifactName)
	{
		return artifactName.startsWith("injected-client-") && NEW_RSA_KEY.exists();
	}

	@Override
	public void apply(Artifact artifact, File workingDir)
	{
		String rsa = getNewModulus();
		if (rsa == null)
		{
			log.error("Failed to read new RSA key.");
			return;
		}

		overwriteModulus(workingDir.toPath(), rsa);
	}

	private String getNewModulus()
	{
		RSAPrivateCrtKey rsaPrivateKey = PrivateKeyReader.readRsaPrivateCrtKey(NEW_RSA_KEY);
		if (rsaPrivateKey != null)
		{
			return rsaPrivateKey.getModulus().toString(16);
		}

		return null;
	}

	protected void overwriteModulus(Path workingDir, String rsa)
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
							int index = indexOf(bytes, "10001".getBytes(java.nio.charset.StandardCharsets.UTF_8));
							if (index != -1)
							{
								log.info("Attempting to patch RSA modulus in file: " + path.getFileName());
								return patchModulus(bytes, rsa);
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
			throw new IllegalStateException("Unable to find modulus.", e);
		}

		throw new IllegalStateException("Unable to find modulus.");
	}

	protected byte[] patchModulus(byte[] original, String replacement)
	{
		int[] sliceIndices = firstSliceIndices(original, 0, 256, b -> isHex((char) (b & 0xFF)));
		int start = sliceIndices[0];
		int end = sliceIndices[1];

		if (start < 2)
		{
			throw new IllegalStateException("Not enough space for 2 length bytes before the slice.");
		}
		char before = (char) (original[start - 1] & 0xFF);
		if (isHex(before))
		{
			throw new IllegalStateException("Invalid range: preceding character is hex.");
		}

		if (end < original.length)
		{
			char after = (char) (original[end] & 0xFF);
			if (isHex(after))
			{
				throw new IllegalStateException("Invalid range: following character is hex.");
			}
		}

		int oldLenByte1 = Byte.toUnsignedInt(original[start - 2]);
		int oldLenByte2 = Byte.toUnsignedInt(original[start - 1]);
		int oldLength = (oldLenByte1 << 8) | oldLenByte2;

		byte[] replacementBytes = replacement.getBytes(StandardCharsets.UTF_8);
		int newLength = replacementBytes.length;

		if (newLength > 0xFFFF)
		{
			throw new IllegalStateException("Replacement length cannot exceed 65535.");
		}

		if (newLength > oldLength)
		{
			throw new IllegalStateException("New modulus cannot be larger than the old.");
		}

		int lengthDelta = newLength - oldLength;
		byte[] newBytes = new byte[original.length + lengthDelta];

		System.arraycopy(original, 0, newBytes, 0, start - 2);

		newBytes[start - 2] = (byte) ((newLength >> 8) & 0xFF);
		newBytes[start - 1] = (byte) (newLength & 0xFF);

		System.arraycopy(replacementBytes, 0, newBytes, start, newLength);

		int oldTailStart = start + oldLength;
		int newTailStart = start + newLength;

		System.arraycopy(
			original,
			oldTailStart,
			newBytes,
			newTailStart,
			original.length - oldTailStart
		);

		return newBytes;
	}

	protected int[] firstSliceIndices(byte[] array, int startIndex, int length, Predicate<Byte> condition)
	{
		int start = startIndex;
		int size = array.length;

		while (true)
		{
			// First locate the starting index where a byte is being accepted
			while (start < size)
			{
				byte b = array[start];
				if (condition.test(b))
				{
					break;
				}
				start++;
			}

			int end = start + 1;
			// Now find the end index where a byte is not being accepted
			while (end < size)
			{
				byte b = array[end];
				if (!condition.test(b))
				{
					break;
				}
				end++;
			}

			if (length != -1 && end - start < length)
			{
				start = end;
				continue;
			}

			return new int[]{start, end};
		}
	}

	protected boolean isHex(char c)
	{
		return (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9');
	}
}
