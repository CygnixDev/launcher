package net.runenite.patches;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;
import static net.runenite.ArtifactPatcher.RESOURCES_DIR;
import net.runenite.Patch;

@Slf4j
public class PatchGameClientPort extends Patch
{
	public static final File PORT_FILE = new File(RESOURCES_DIR, "injected-client/port.txt");

	@Override
	public boolean appliesTo(String artifactName)
	{
		return artifactName.startsWith("injected-client-") && PORT_FILE.exists();
	}

	@Override
	public void apply(Artifact artifact, File workingDir)
	{
		String replacementPort = getReplacementPort();
		if (replacementPort == null)
		{
			log.error("Failed to read replacement port form.");
			return;
		}

		try
		{
			patchPorts(workingDir.toPath(), 43594, Integer.parseInt(replacementPort.trim()));
		}
		catch (Exception e)
		{
			log.error("Error patching game port", e);
		}
	}

	protected void patchPorts(Path workingDir, int searchPort, int replacePort)
	{
		if (searchPort == replacePort)
		{
			return;
		}

		byte[] searchPortBytes = buildPortBytes(searchPort);
		byte[] replacePortBytes = buildPortBytes(replacePort);

		if (searchPortBytes.length != replacePortBytes.length)
		{
			throw new IllegalArgumentException("searchPort and replacePort must have the same byte size.");
		}

		boolean patched = false;

		try (Stream<Path> paths = Files.walk(workingDir))
		{
			for (Path path : (Iterable<Path>) paths::iterator)
			{
				if (Files.isRegularFile(path))
				{
					try
					{
						boolean success = modifyFile(path.toFile(), bytes -> {
							int index = indexOf(bytes, searchPortBytes);
							if (index != -1)
							{
								log.info("Attempting to patch port from {} to {} in {}", searchPort, replacePort, path.getFileName());
								System.arraycopy(replacePortBytes, 0, bytes, index, replacePortBytes.length);
								return bytes;
							}

							return null;
						});

						if (success)
						{
							patched = true;
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
			throw new IllegalStateException("Unable to find port.", e);
		}

		if (!patched)
		{
			throw new IllegalStateException("Unable to find port.");
		}
	}

	private String getReplacementPort()
	{
		try
		{
			return Files.readString(PORT_FILE.toPath());
		}
		catch (IOException e)
		{
			return null;
		}
	}

	private byte[] buildPortBytes(int port)
	{
		return new byte[]{
			3, 0, 0,
			(byte) ((port >>> 8) & 0xFF),
			(byte) (port & 0xFF)
		};
	}
}
