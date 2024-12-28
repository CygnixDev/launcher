package net.runenite;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.runelite.launcher.beans.Artifact;

@Slf4j
public abstract class Patch
{
	public abstract boolean appliesTo(String artifactName);

	public abstract void apply(Artifact artifact, File workingDir);

	protected int indexOf(byte[] array, byte[] target)
	{
		outer:
		for (int i = 0; i <= array.length - target.length; i++)
		{
			for (int j = 0; j < target.length; j++)
			{
				if (array[i + j] != target[j])
				{
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	protected boolean modifyFile(File file, Function<byte[], byte[]> modifyAction)
	{
		try
		{
			byte[] originalBytes = Files.readAllBytes(file.toPath());
			byte[] modifiedBytes = modifyAction.apply(originalBytes);
			if (modifiedBytes == null)
			{
				return false;
			}

			Files.write(file.toPath(), modifiedBytes);
			return true;
		}
		catch (IOException e)
		{
			log.error("Error reading or writing file: {}", file.getAbsolutePath(), e);
			return false;
		}
	}
}
