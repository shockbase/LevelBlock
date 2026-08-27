param(
    [string]$Source = "$PSScriptRoot\..\resourcepack\assets\levelblock\textures\item\forcefield.png",
    [string]$ResourcepackRoot = "$PSScriptRoot\..\resourcepack"
)

Add-Type -AssemblyName System.Drawing
$null = [System.Drawing.Bitmap]
$null = [System.Drawing.Color]

if (-not ("LevelBlockForcefieldGenerator" -as [type])) {
    $drawingAssemblies = [AppDomain]::CurrentDomain.GetAssemblies() |
        Where-Object {
            $_.Location -and $_.GetName().Name -in @(
                'System.Drawing.Common',
                'System.Drawing.Primitives',
                'System.Private.Windows.Core',
                'System.Private.Windows.GdiPlus'
            )
        } |
        Select-Object -ExpandProperty Location
    Add-Type -ReferencedAssemblies $drawingAssemblies -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

public static class LevelBlockForcefieldGenerator
{
    private const int PhaseCount = 1;
    private const int FrameSize = 64;
    private const int FrameCount = 60;

    public static void Generate(string sourcePath, string outputDirectory)
    {
        using var source = new Bitmap(sourcePath);
        if (source.Width != 16 || source.Height != 16)
        {
            throw new InvalidOperationException("forcefield.png muss 16x16 Pixel gross sein.");
        }

        var pixels = new Color[source.Width, source.Height];
        int minAlpha = 255;
        int maxAlpha = 0;
        for (int y = 0; y < source.Height; y++)
        {
            for (int x = 0; x < source.Width; x++)
            {
                Color color = source.GetPixel(x, y);
                pixels[x, y] = color;
                minAlpha = Math.Min(minAlpha, color.A);
                maxAlpha = Math.Max(maxAlpha, color.A);
            }
        }

        Directory.CreateDirectory(outputDirectory);
        for (int uPhase = 0; uPhase < PhaseCount; uPhase++)
        {
            for (int vPhase = 0; vPhase < PhaseCount; vPhase++)
            {
                using var output = new Bitmap(
                    FrameSize,
                    FrameSize * FrameCount,
                    PixelFormat.Format32bppArgb
                );
                var rectangle = new Rectangle(0, 0, output.Width, output.Height);
                BitmapData bitmapData = output.LockBits(
                    rectangle,
                    ImageLockMode.WriteOnly,
                    PixelFormat.Format32bppArgb
                );
                byte[] data = new byte[bitmapData.Stride * bitmapData.Height];

                for (int frame = 0; frame < FrameCount; frame++)
                {
                    double offset = frame / (double)FrameCount;
                    for (int y = 0; y < FrameSize; y++)
                    {
                        double verticalPosition = (y + 0.5) / FrameSize;
                        double gradient = Clamp01((verticalPosition - 0.5) * 2.0);
                        double localV = (y + 0.5) / FrameSize;
                        double sampleV = vPhase / (double)PhaseCount
                                + localV / PhaseCount
                                + offset;

                        for (int x = 0; x < FrameSize; x++)
                        {
                            double localU = (x + 0.5) / FrameSize;
                            double sampleU = uPhase / (double)PhaseCount
                                    + localU / PhaseCount
                                    + offset;
                            double[] color = SampleBilinear(pixels, sampleU, sampleV);
                            double patternAlpha = maxAlpha == minAlpha
                                    ? 1.0
                                    : Clamp01((color[3] - minAlpha) / (maxAlpha - minAlpha));
                            int alpha = (int)Math.Round(255.0 * gradient * patternAlpha);

                            int row = frame * FrameSize + y;
                            int index = row * bitmapData.Stride + x * 4;
                            data[index] = ToByte(color[2]);
                            data[index + 1] = ToByte(color[1]);
                            data[index + 2] = ToByte(color[0]);
                            data[index + 3] = (byte)alpha;
                        }
                    }
                }

                Marshal.Copy(data, 0, bitmapData.Scan0, data.Length);
                output.UnlockBits(bitmapData);
                string name = $"forcefield_u{uPhase}_v{vPhase}.png";
                output.Save(Path.Combine(outputDirectory, name), ImageFormat.Png);
            }
        }
    }

    private static double[] SampleBilinear(Color[,] pixels, double u, double v)
    {
        int width = pixels.GetLength(0);
        int height = pixels.GetLength(1);
        double x = Wrap01(u) * width - 0.5;
        double y = Wrap01(v) * height - 0.5;
        int x0 = (int)Math.Floor(x);
        int y0 = (int)Math.Floor(y);
        double tx = x - Math.Floor(x);
        double ty = y - Math.Floor(y);

        Color c00 = pixels[FloorMod(x0, width), FloorMod(y0, height)];
        Color c10 = pixels[FloorMod(x0 + 1, width), FloorMod(y0, height)];
        Color c01 = pixels[FloorMod(x0, width), FloorMod(y0 + 1, height)];
        Color c11 = pixels[FloorMod(x0 + 1, width), FloorMod(y0 + 1, height)];

        return new[]
        {
            Bilinear(c00.R, c10.R, c01.R, c11.R, tx, ty),
            Bilinear(c00.G, c10.G, c01.G, c11.G, tx, ty),
            Bilinear(c00.B, c10.B, c01.B, c11.B, tx, ty),
            Bilinear(c00.A, c10.A, c01.A, c11.A, tx, ty)
        };
    }

    private static double Bilinear(double c00, double c10, double c01, double c11, double tx, double ty)
    {
        double top = c00 + (c10 - c00) * tx;
        double bottom = c01 + (c11 - c01) * tx;
        return top + (bottom - top) * ty;
    }

    private static double Wrap01(double value) => value - Math.Floor(value);

    private static int FloorMod(int value, int modulus)
    {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    private static double Clamp01(double value) => Math.Max(0.0, Math.Min(1.0, value));

    private static byte ToByte(double value) => (byte)Math.Round(Math.Max(0.0, Math.Min(255.0, value)));
}
'@
}

$textureDirectory = Join-Path $ResourcepackRoot 'assets\levelblock\textures\item'
$modelDirectory = Join-Path $ResourcepackRoot 'assets\levelblock\models\item'
$itemDirectory = Join-Path $ResourcepackRoot 'assets\levelblock\items'
New-Item -ItemType Directory -Force $textureDirectory, $modelDirectory, $itemDirectory | Out-Null

Get-ChildItem -LiteralPath $textureDirectory -File |
    Where-Object { $_.Name -match '^forcefield_u\d+_v\d+\.png(?:\.mcmeta)?$' } |
    Remove-Item -Force
Get-ChildItem -LiteralPath $modelDirectory -File |
    Where-Object { $_.Name -match '^forcefield_u\d+_v\d+\.json$' } |
    Remove-Item -Force
Get-ChildItem -LiteralPath $itemDirectory -File |
    Where-Object { $_.Name -match '^forcefield_u\d+_v\d+\.json$' } |
    Remove-Item -Force

[LevelBlockForcefieldGenerator]::Generate((Resolve-Path $Source).Path, $textureDirectory)

$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($uPhase in 0) {
    foreach ($vPhase in 0) {
        $name = "forcefield_u${uPhase}_v${vPhase}"
        $itemJson = @"
{
  "model": {
    "type": "minecraft:model",
    "model": "levelblock:item/$name",
    "tints": [
      {
        "type": "minecraft:custom_model_data",
        "index": 0,
        "default": 16724016
      }
    ]
  }
}
"@
        $modelJson = @"
{
  "ambientocclusion": false,
  "gui_light": "front",
  "textures": {
    "forcefield": {
      "sprite": "levelblock:item/$name",
      "force_translucent": true
    }
  },
  "elements": [
    {
      "from": [0, 0, 7.99],
      "to": [16, 16, 8.01],
      "shade": false,
      "faces": {
        "north": { "uv": [0, 0, 16, 16], "texture": "#forcefield", "tintindex": 0 },
        "south": { "uv": [0, 0, 16, 16], "texture": "#forcefield", "tintindex": 0 }
      }
    }
  ]
}
"@
        $animationJson = @"
{
  "animation": {
    "frametime": 1,
    "interpolate": true
  }
}
"@

        [IO.File]::WriteAllText((Join-Path $itemDirectory "$name.json"), $itemJson, $utf8)
        [IO.File]::WriteAllText((Join-Path $modelDirectory "$name.json"), $modelJson, $utf8)
        [IO.File]::WriteAllText((Join-Path $textureDirectory "$name.png.mcmeta"), $animationJson, $utf8)
    }
}
