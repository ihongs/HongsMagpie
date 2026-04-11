#!/usr/bin/env python3
import os
import sys
import argparse
from dotenv import load_dotenv
from markitdown import MarkItDown

# 加载 .env 文件
load_dotenv()

# 配置变量
CONFIG = {
    'ocr' : bool(os.getenv('OCR', 'False')),
    'llm_model': os.getenv('LLM_MODEL'),
    'llm_api_key': os.getenv('LLM_API_KEY'),
    'llm_base_url': os.getenv('LLM_BASE_URL')
}

def parse(filepath=None):
    try:
        # 使用 MarkItDown 库处理文件
        if CONFIG['ocr']:
            # 启用 OCR 插件
            from openai import OpenAI
            md = MarkItDown(
                enable_plugins=True,
                llm_model=CONFIG['llm_model'],
                llm_client=OpenAI(
                    api_key=CONFIG['llm_api_key'],
                    base_url=CONFIG['llm_base_url']
                )
            )
        else:
            md = MarkItDown()
        
        if filepath:
            data = md.convert(filepath)
        else:
            data = md.convert_stream(sys.stdin.buffer)
        
        text = data.text_content
        
        print(text)
        sys.exit(0)
    except Exception as e:
        print(f'ERROR: Processing error: {str(e)}', file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Markdown Parser')
    parser.add_argument('--file', type=str, required=False, help='File to convert')
    parser.add_argument('--ocr' , action='store_true', help='Enable OCR')
    parser.add_argument('--llm-model', type=str, default=None, help='LLM Model')
    parser.add_argument('--llm-api-key', type=str, default=None, help='LLM API key')
    parser.add_argument('--llm-base-url', type=str, default=None, help='LLM base URL')
    
    args = parser.parse_args()
    
    # 更新配置
    if args.ocr:
        CONFIG['ocr'] = True
    if args.llm_model:
        CONFIG['llm_model'] = args.llm_model
    if args.llm_api_key:
        CONFIG['llm_api_key'] = args.llm_api_key
    if args.llm_base_url:
        CONFIG['llm_base_url'] = args.llm_base_url
    
    filepath = args.file
    
    if filepath and not os.path.exists(filepath):
        print(f'ERROR: File not found: {filepath}', file=sys.stderr)
        sys.exit(1)
    
    parse(filepath)
