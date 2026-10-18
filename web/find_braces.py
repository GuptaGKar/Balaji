with open('src/app/(admin)/orders/page.tsx', 'r') as f:
    content = f.read()
lines = content.split('\n')
for i, line in enumerate(lines, 1):
    open_count = line.count('{')
    close_count = line.count('}')
    if open_count != close_count:
        print(f'Line {i+1}: open={line.count("{")}, close={line.count("}")} | {line.strip()[:80]}')