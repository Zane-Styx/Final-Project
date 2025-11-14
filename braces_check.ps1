$path = 'c:\Users\Styx\Desktop\Final-Project\Chromashift\core\src\main\java\com\jjmc\chromashift\screens\LevelMakerScreen.java'
$d = 0
$i = 1
Get-Content $path | ForEach-Object {
  $line = $_
  foreach ($c in ($line.ToCharArray())) {
    if ($c -eq '{') { $d++ } elseif ($c -eq '}') { $d-- }
  }
  "{0,4}: depth={1} | {2}" -f $i, $d, $line
  $i++
} | Out-File -Encoding utf8 'c:\Users\Styx\Desktop\Final-Project\Chromashift\braces_report.txt'
