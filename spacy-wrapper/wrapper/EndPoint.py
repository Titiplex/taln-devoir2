#!/usr/bin/env python

import json

import spacy
from py4j.java_gateway import JavaGateway, GatewayParameters, CallbackServerParameters

# Lazy-loaded caches
_NLP_SM = None
_NLP_MD = None
_NLP_LG = None


def _extract_labels(doc, target: str | None = None):
    if target is not None and target != "":
        t = target.strip()
        return [ent.label_ for ent in doc.ents if ent.text == t]
    return [ent.label_ for ent in doc.ents]


# noinspection PyPep8Naming
class EndPoint(object):
    """
    Initialized thanks to [GeeksForGeeks](https://www.geeksforgeeks.org/python/python-named-entity-recognition-ner-using-spacy).
    """
    class Java:
        implements = ['udem.taln.wrapper.WrapperInterface']

    @staticmethod
    def processLG(sentence: str, target: str = "") -> str:
        global _NLP_LG
        if _NLP_LG is None:
            print("Loading spaCy model: en_core_web_lg ...")
            try:
                _NLP_LG = spacy.load('en_core_web_lg')
                print("Loaded en_core_web_lg")
            except Exception as e:
                raise RuntimeError(f"Failed to load en_core_web_lg: {e}")
        doc = _NLP_LG(sentence)
        return json.dumps({"labels": _extract_labels(doc, target)})

    @staticmethod
    def processMD(sentence: str, target: str = "") -> str:
        global _NLP_MD
        if _NLP_MD is None:
            print("Loading spaCy model: en_core_web_md ...")
            try:
                _NLP_MD = spacy.load('en_core_web_md')
                print("Loaded en_core_web_md")
            except Exception as e:
                raise RuntimeError(f"Failed to load en_core_web_md: {e}")
        doc = _NLP_MD(sentence)
        return json.dumps({"labels": _extract_labels(doc, target)})

    @staticmethod
    def processSM(sentence: str, target: str = "") -> str:
        global _NLP_SM
        if _NLP_SM is None:
            print("Loading spaCy model: en_core_web_sm ...")
            try:
                _NLP_SM = spacy.load('en_core_web_sm')
                print("Loaded en_core_web_sm")
            except Exception as e:
                raise RuntimeError(f"Failed to load en_core_web_sm: {e}")
        doc = _NLP_SM(sentence)
        return json.dumps({"labels": _extract_labels(doc, target)})


if __name__ == "__main__":
    gateway = JavaGateway(
        gateway_parameters=GatewayParameters(address="127.0.0.1", port=25333),
        callback_server_parameters=CallbackServerParameters()
    )
    gateway.entry_point.registerPythonObject(EndPoint())
    print("Python side registered. Waiting for calls...")
    import time

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
