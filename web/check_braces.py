with open('src/app/(admin)/orders/page.tsx', 'r') as f:
    content = f.read()
lines = content.split('\n')
for i, line in enumerate(content.split('\n'), 1):
    oc = line.count('{')
    cc = line.count('}')
    if oc != cc:
        print(f'Line {i+1}: open={line.count("{")}, close={line.count("}")} | {line.strip()[:80]}')