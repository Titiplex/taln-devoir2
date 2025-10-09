# Devoir 2 TALN IFT-6285

You can find this project repository [here](https://github.com/Titiplex/taln-devoir2)

This project is organized in 2 modules :
- [main-project](main-project)
- [spacy-wrapper](spacy-wrapper)

## Main-project

This is the java part of the project, which is also the main part. 
It contains the following packages :
- [ner](main-project/src/main/java/udem/taln/ner) contains the NER interaction class.
- [wrapper](main-project/src/main/java/udem/taln/wrapper) contains the wrapper for python "Spacy" package.
  - [parsers](main-project/src/main/java/udem/taln/wrapper/parsers) parsing for json responses.
  - [dto](main-project/src/main/java/udem/taln/wrapper/dto) data transfer objects.

The main class is [Main](main-project/src/main/java/udem/taln/Main.java), which parses args and sens to the correct class.

## Spacy-wrapper

This is the python part of the project, which is really only an interface to the wrapper of the spacy package.