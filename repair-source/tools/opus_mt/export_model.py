#!/usr/bin/env python3
"""Prepare a local OPUS-MT EN->TR ONNX package for GameLingo.

Run on a PC or GitHub Actions machine with Internet. This is an actual Marian
model conversion, NOT a JSON phrase dictionary. The app itself needs no cloud
API at translation time. The final ZIP is imported from Android's file picker.
"""
import argparse
import json
import pathlib
import shutil
import zipfile

MODEL = 'Helsinki-NLP/opus-tatoeba-en-tr'  # Apache-2.0
NEEDED = ('encoder_model.onnx', 'decoder_model.onnx', 'source.spm', 'target.spm', 'vocab.json', 'model_info.json')


def export(output: pathlib.Path):
    from huggingface_hub import snapshot_download
    from optimum.exporters.onnx import main_export
    from transformers import AutoConfig, MarianTokenizer

    output.mkdir(parents=True, exist_ok=True)
    cache = output / 'source_files'
    snapshot_download(
        repo_id=MODEL,
        local_dir=cache,
        allow_patterns=['*.spm', '*.json', '*.safetensors', 'pytorch_model.bin', 'README.md'],
    )
    # Explicitly export a decoder WITHOUT cache; the Android runner uses
    # greedy autoregressive generation with the full decoder token history.
    main_export(model_name_or_path=str(cache), output=output / 'onnx', task='text2text-generation',
                no_post_process=True)
    import onnx
    for name in ('encoder_model.onnx', 'decoder_model.onnx'):
        generated = output / 'onnx' / name
        if not generated.is_file():
            raise RuntimeError(f'Model export failed: {name} missing. Cannot publish a fake pack.')
        # Optimum may create external .onnx_data weights even for small models.
        # Android importer requires a standalone ONNX file for each component.
        graph = onnx.load(str(generated), load_external_data=True)
        onnx.save_model(graph, str(output / name), save_as_external_data=False)
    for name in ('source.spm', 'target.spm', 'vocab.json'):
        shutil.copy2(cache / name, output / name)
    cfg = AutoConfig.from_pretrained(cache)
    tokenizer = MarianTokenizer.from_pretrained(cache)
    metadata = {
        'format_version': 1,
        'model_id': MODEL,
        'model_license': 'Apache-2.0',
        'eos_token_id': int(cfg.eos_token_id),
        'pad_token_id': int(cfg.pad_token_id),
        'decoder_start_token_id': int(cfg.decoder_start_token_id),
        'max_source_tokens': 160,
        'algorithm': 'greedy_no_past',
    }
    assert metadata['eos_token_id'] == tokenizer.eos_token_id
    (output / 'model_info.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')

    # Fail early if ONNX tensor names do not match Android runner's contract.
    encoder = onnx.load(str(output / 'encoder_model.onnx'), load_external_data=False)
    decoder = onnx.load(str(output / 'decoder_model.onnx'), load_external_data=False)
    enc_inputs = {x.name for x in encoder.graph.input}
    dec_inputs = {x.name for x in decoder.graph.input}
    assert 'input_ids' in enc_inputs, f'Unexpected encoder inputs: {enc_inputs}'
    assert dec_inputs & {'input_ids', 'decoder_input_ids'}, f'Unexpected decoder inputs: {dec_inputs}'
    assert dec_inputs & {'encoder_hidden_states', 'encoder_outputs'}, f'Unexpected decoder inputs: {dec_inputs}'
    assert any(x.name == 'logits' for x in decoder.graph.output)
    assert not any('past_key_values' in x for x in dec_inputs), 'Model needs decoder without KV cache'
    for graph in (encoder, decoder):
        for tensor in graph.graph.initializer:
            assert tensor.data_location != onnx.TensorProto.EXTERNAL, 'ONNX external weights not packed'
    # ONNX external tensors cannot be imported by this single-file ZIP format.
    assert all((output / name).stat().st_size > 100_000 for name in NEEDED[:2])
    print('OPUS model pack exported; input/output schema verified.')


def package(root: pathlib.Path, target: pathlib.Path):
    assert all((root / name).is_file() for name in NEEDED), 'Model files missing'
    with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_STORED, allowZip64=True) as z:
        for name in NEEDED:
            z.write(root / name, arcname=name)
    print(f'Created {target}: {target.stat().st_size:,} bytes')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default='opus_mt_output')
    args = ap.parse_args()
    out = pathlib.Path(args.out)
    export(out)
    package(out, out / 'GameLingo_OPUS_MT_en_tr_model.zip')
